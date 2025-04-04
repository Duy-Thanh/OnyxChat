const express = require('express');
const { body, validationResult } = require('express-validator');
const { v4: uuidv4 } = require('uuid');
const { 
  generateAccessToken, 
  generateRefreshToken, 
  verifyToken, 
  hashPassword, 
  comparePassword 
} = require('../utils/auth.utils');
const { 
  ValidationError, 
  AuthenticationError, 
  ConflictError, 
  NotFoundError 
} = require('../utils/error.utils');
const { authenticate } = require('../middleware/auth.middleware');
const db = require('../models');

const router = express.Router();

/**
 * Validate request body
 */
const validate = (req, res, next) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    return next(new ValidationError('Validation error', errors.array()));
  }
  next();
};

/**
 * @route POST /api/auth/register
 * @desc Register a new user
 * @access Public
 */
router.post('/register', [
  body('username')
    .isLength({ min: 3, max: 30 })
    .withMessage('Username must be between 3 and 30 characters')
    .isAlphanumeric()
    .withMessage('Username must contain only letters and numbers'),
  body('email')
    .isEmail()
    .withMessage('Please provide a valid email address'),
  body('password')
    .isLength({ min: 8 })
    .withMessage('Password must be at least 8 characters long'),
  body('displayName')
    .optional()
    .isLength({ min: 2, max: 50 })
    .withMessage('Display name must be between 2 and 50 characters'),
  validate
], async (req, res, next) => {
  try {
    const { username, email, password, displayName } = req.body;
    
    // Check if username already exists
    const existingUsername = await db.User.findOne({ 
      where: { username }
    });
    
    if (existingUsername) {
      return next(new ConflictError('Username'));
    }
    
    // Check if email already exists
    const existingEmail = await db.User.findOne({ 
      where: { email }
    });
    
    if (existingEmail) {
      return next(new ConflictError('Email'));
    }
    
    // Hash password
    const passwordHash = await hashPassword(password);
    
    // Create user
    const user = await db.User.create({
      username,
      email,
      passwordHash,
      displayName: displayName || username,
      isActive: true,
      lastActiveAt: new Date()
    });
    
    // Generate tokens
    const accessToken = generateAccessToken(user.id);
    const refreshToken = generateRefreshToken(user.id);
    
    // Store refresh token
    await db.RefreshToken.create({
      token: refreshToken,
      userId: user.id,
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) // 7 days
    });
    
    res.status(201).json({
      status: 'success',
      message: 'User registered successfully',
      data: {
        user: user.toProfile(),
        tokens: {
          accessToken,
          refreshToken
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/auth/login
 * @desc Authenticate user and get token
 * @access Public
 */
router.post('/login', [
  body('username')
    .notEmpty()
    .withMessage('Username is required'),
  body('password')
    .notEmpty()
    .withMessage('Password is required'),
  validate
], async (req, res, next) => {
  try {
    const { username, password } = req.body;
    
    // Find user by username
    const user = await db.User.findOne({ 
      where: { username }
    });
    
    if (!user) {
      return next(new AuthenticationError('Invalid credentials'));
    }
    
    // Check password
    const isMatch = await comparePassword(password, user.passwordHash);
    if (!isMatch) {
      return next(new AuthenticationError('Invalid credentials'));
    }
    
    // Update last active - always use update method for compatibility with mock mode
    await db.User.update(
      { lastActiveAt: new Date(), isActive: true },
      { where: { id: user.id } }
    );
    
    // Generate tokens
    const accessToken = generateAccessToken(user.id);
    const refreshToken = generateRefreshToken(user.id);
    
    // Store refresh token
    await db.RefreshToken.create({
      token: refreshToken,
      userId: user.id,
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) // 7 days
    });
    
    res.json({
      status: 'success',
      message: 'Login successful',
      data: {
        user: user.toProfile(),
        tokens: {
          accessToken,
          refreshToken
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/auth/refresh
 * @desc Refresh access token
 * @access Public
 */
router.post('/refresh', [
  body('refreshToken')
    .notEmpty()
    .withMessage('Refresh token is required'),
  validate
], async (req, res, next) => {
  try {
    const { refreshToken } = req.body;
    
    // Find token in database
    const tokenDoc = await db.RefreshToken.findOne({ 
      where: { token: refreshToken, revoked: false }
    });
    
    if (!tokenDoc) {
      return next(new AuthenticationError('Invalid refresh token'));
    }
    
    // Check if token is expired
    if (new Date(tokenDoc.expiresAt) < new Date()) {
      await tokenDoc.update({ revoked: true, revokedAt: new Date() });
      return next(new AuthenticationError('Refresh token expired'));
    }
    
    // Verify token
    let decoded;
    try {
      decoded = verifyToken(refreshToken, true);
    } catch (error) {
      await tokenDoc.update({ revoked: true, revokedAt: new Date() });
      return next(new AuthenticationError('Invalid refresh token'));
    }
    
    // Find user
    const user = await db.User.findByPk(decoded.sub);
    if (!user) {
      return next(new AuthenticationError('User not found'));
    }
    
    // Generate new tokens
    const accessToken = generateAccessToken(user.id);
    const newRefreshToken = generateRefreshToken(user.id);
    
    // Revoke old token
    await tokenDoc.update({ revoked: true, revokedAt: new Date() });
    
    // Store new refresh token
    await db.RefreshToken.create({
      token: newRefreshToken,
      userId: user.id,
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) // 7 days
    });
    
    res.json({
      status: 'success',
      message: 'Token refreshed successfully',
      data: {
        tokens: {
          accessToken,
          refreshToken: newRefreshToken
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/auth/logout
 * @desc Logout user and revoke refresh token
 * @access Protected
 */
router.post('/logout', authenticate, [
  body('refreshToken')
    .notEmpty()
    .withMessage('Refresh token is required'),
  validate
], async (req, res, next) => {
  try {
    const { refreshToken } = req.body;
    
    // Revoke refresh token
    await db.RefreshToken.update(
      { revoked: true, revokedAt: new Date() },
      { where: { token: refreshToken, userId: req.user.id } }
    );
    
    // Update user status
    await req.user.update({ isActive: false });
    
    res.json({
      status: 'success',
      message: 'Logout successful'
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/auth/me
 * @desc Get current user
 * @access Protected
 */
router.get('/me', authenticate, async (req, res, next) => {
  try {
    res.json({
      status: 'success',
      data: {
        user: req.user.toProfile()
      }
    });
  } catch (error) {
    next(error);
  }
});

module.exports = router; 