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
const jwt = require('jsonwebtoken');
const emailService = require('../services/email.service');

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
    .withMessage('Username or email is required'),
  body('password')
    .notEmpty()
    .withMessage('Password is required'),
  validate
], async (req, res, next) => {
  try {
    const { username, password } = req.body;
    
    // Find user by username or email
    const user = await db.User.findOne({ 
      where: {
        [db.Sequelize.Op.or]: [
          { username: username },
          { email: username }  // Allow login with email in the username field
        ]
      }
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
    
    console.log(`Received refresh token request: ${refreshToken.substring(0, 10)}...`);
    
    // Make sure we're using the database version, not mock
    console.log('Looking up token in SQL database using RefreshToken model');
    
    // Find token in database with detailed logging
    let tokenDoc = null;
    try {
      // Log the SQL being executed
      console.log(`SELECT * FROM refresh_tokens WHERE token = '${refreshToken.substring(0, 10)}...' AND revoked = false`);
      
      tokenDoc = await db.RefreshToken.findOne({ 
        where: { token: refreshToken, revoked: false }
      });
      
      if (tokenDoc) {
        console.log(`Token found in database: ${JSON.stringify({
          id: tokenDoc.id,
          userId: tokenDoc.userId,
          expiresAt: tokenDoc.expiresAt,
          revoked: tokenDoc.revoked
        })}`);
      } else {
        console.log(`Token not found in database: ${refreshToken.substring(0, 10)}...`);
      }
    } catch (dbError) {
      console.error(`Database error when finding token: ${dbError.message}`);
      console.error(dbError.stack);
      return next(new Error(`Database error when finding token: ${dbError.message}`));
    }
    
    if (!tokenDoc) {
      console.log(`Refresh token not found in database or already revoked`);
      return next(new AuthenticationError('Invalid refresh token'));
    }
    
    // Check if token is expired
    if (new Date(tokenDoc.expiresAt) < new Date()) {
      console.log(`Refresh token expired at ${tokenDoc.expiresAt}`);
      await tokenDoc.update({ revoked: true, revokedAt: new Date() });
      return next(new AuthenticationError('Refresh token expired'));
    }
    
    // Verify token cryptographically
    let decoded;
    try {
      decoded = verifyToken(refreshToken, true);
      console.log(`Refresh token verified successfully for user: ${decoded.sub}`);
    } catch (error) {
      console.error(`Error verifying refresh token: ${error.message}`);
      await tokenDoc.update({ revoked: true, revokedAt: new Date() });
      return next(new AuthenticationError(`Invalid refresh token: ${error.message}`));
    }
    
    // Find user
    const user = await db.User.findByPk(decoded.sub);
    if (!user) {
      console.error(`User not found for ID: ${decoded.sub}`);
      return next(new AuthenticationError('User not found'));
    }
    
    // Update user's last active time
    await user.update({ lastActiveAt: new Date(), isActive: true });
    
    // Generate new tokens
    const accessToken = generateAccessToken(user.id);
    const newRefreshToken = generateRefreshToken(user.id);
    
    console.log(`Generated new tokens for user ${user.id}: access=${accessToken.substring(0, 10)}..., refresh=${newRefreshToken.substring(0, 10)}...`);
    
    // Revoke old token
    try {
      await tokenDoc.update({ revoked: true, revokedAt: new Date() });
      console.log(`Old token revoked: ${refreshToken.substring(0, 10)}...`);
    } catch (updateError) {
      console.error(`Error revoking old token: ${updateError.message}`);
      // Continue anyway, this is not fatal
    }
    
    // Store new refresh token
    try {
      const newTokenRecord = await db.RefreshToken.create({
        token: newRefreshToken,
        userId: user.id,
        expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) // 7 days
      });
      console.log(`New token stored with ID: ${newTokenRecord.id}`);
    } catch (createError) {
      console.error(`Error storing new token: ${createError.message}`);
      // This is more serious, but we can still return the token to the client
      // They just won't be able to refresh it later
    }
    
    // Always include user data in the response
    res.json({
      status: 'success',
      message: 'Token refreshed successfully',
      data: {
        user: user.toProfile(),
        tokens: {
          accessToken,
          refreshToken: newRefreshToken
        }
      }
    });
  } catch (error) {
    console.error(`Error in refresh token endpoint: ${error.message}`, error);
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

/**
 * @route POST /api/auth/request-password-reset
 * @desc Request a password reset by sending OTP to email
 * @access Public
 */
router.post('/request-password-reset', [
  body('email')
    .isEmail()
    .withMessage('Please provide a valid email address'),
  body('username')
    .optional()
    .isString()
    .withMessage('Username must be a string'),
  validate
], async (req, res, next) => {
  try {
    const { email, username } = req.body;
    
    // Find user by email
    const whereClause = { email };
    
    // If username is provided, add it to the where clause
    if (username) {
      whereClause.username = username;
    }
    
    const user = await db.User.findOne({ 
      where: whereClause
    });
    
    if (!user) {
      // Don't reveal if email exists for security reasons
      return res.json({
        status: 'success',
        message: 'If your email is registered, you will receive a reset code shortly.'
      });
    }
    
    // Generate a 6-digit OTP code
    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    
    // Hash the OTP for storage
    const otpHash = await hashPassword(otp);
    
    // Store OTP in database with expiration (15 minutes)
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000);
    
    await db.PasswordReset.upsert({
      userId: user.id,
      otpHash,
      expiresAt,
      attempts: 0
    });
    
    // Send email with OTP
    let emailSent = false;
    
    if (process.env.NODE_ENV === 'production' && !username) {
      // In production, send actual email if username is not provided
      emailSent = await emailService.sendOtpEmail(email, otp);
      
      if (!emailSent) {
        console.error(`Failed to send OTP email to ${email}`);
      }
    } else {
      // In development, log OTP to console
      console.log(`Password reset OTP for ${email}: ${otp}`);
      emailSent = true;
    }
    
    // If both username and email match, or in development mode, return OTP in response
    if (username || process.env.NODE_ENV === 'development') {
      return res.json({
        status: 'success',
        message: 'Password reset code generated successfully.',
        data: {
          otp: otp
        }
      });
    }
    
    res.json({
      status: 'success',
      message: emailSent 
        ? 'Password reset code sent to your email.' 
        : 'If your email is registered, you will receive a reset code shortly.'
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/auth/verify-reset-otp
 * @desc Verify OTP for password reset
 * @access Public
 */
router.post('/verify-reset-otp', [
  body('email')
    .isEmail()
    .withMessage('Please provide a valid email address'),
  body('otp')
    .isLength({ min: 6, max: 6 })
    .isNumeric()
    .withMessage('OTP must be a 6-digit number'),
  validate
], async (req, res, next) => {
  try {
    const { email, otp } = req.body;
    
    // Find user by email
    const user = await db.User.findOne({ 
      where: { email }
    });
    
    if (!user) {
      return next(new NotFoundError('User not found'));
    }
    
    // Find password reset record
    const resetRecord = await db.PasswordReset.findOne({
      where: { userId: user.id }
    });
    
    if (!resetRecord) {
      return next(new NotFoundError('No password reset request found'));
    }
    
    // Check if OTP is expired
    if (new Date(resetRecord.expiresAt) < new Date()) {
      return next(new AuthenticationError('OTP has expired'));
    }
    
    // Check if too many attempts
    if (resetRecord.attempts >= 5) {
      return next(new AuthenticationError('Too many failed attempts. Please request a new OTP.'));
    }
    
    // Verify OTP
    const isMatch = await comparePassword(otp, resetRecord.otpHash);
    
    if (!isMatch) {
      // Increment attempts
      await resetRecord.update({ attempts: resetRecord.attempts + 1 });
      return next(new AuthenticationError('Invalid OTP'));
    }
    
    // Generate a temporary token for password reset
    const resetToken = jwt.sign(
      { 
        sub: user.id,
        type: 'reset',
        iat: Math.floor(Date.now() / 1000)
      },
      process.env.JWT_SECRET,
      { expiresIn: '15m' }
    );
    
    res.json({
      status: 'success',
      message: 'OTP verified successfully',
      data: {
        resetToken
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/auth/reset-password
 * @desc Reset password using reset token
 * @access Public
 */
router.post('/reset-password', [
  body('resetToken')
    .notEmpty()
    .withMessage('Reset token is required'),
  body('newPassword')
    .isLength({ min: 8 })
    .withMessage('Password must be at least 8 characters long'),
  validate
], async (req, res, next) => {
  try {
    const { resetToken, newPassword } = req.body;
    
    // Verify reset token
    let decoded;
    try {
      decoded = jwt.verify(resetToken, process.env.JWT_SECRET);
      
      // Check token type
      if (decoded.type !== 'reset') {
        return next(new AuthenticationError('Invalid token type'));
      }
    } catch (error) {
      return next(new AuthenticationError('Invalid or expired reset token'));
    }
    
    // Find user
    const user = await db.User.findByPk(decoded.sub);
    if (!user) {
      return next(new NotFoundError('User not found'));
    }
    
    // Hash new password
    const passwordHash = await hashPassword(newPassword);
    
    // Update user password
    await user.update({ passwordHash });
    
    // Delete password reset record
    await db.PasswordReset.destroy({
      where: { userId: user.id }
    });
    
    // Revoke all refresh tokens for this user
    await db.RefreshToken.update(
      { revoked: true, revokedAt: new Date() },
      { where: { userId: user.id } }
    );
    
    res.json({
      status: 'success',
      message: 'Password reset successful'
    });
  } catch (error) {
    next(error);
  }
});

module.exports = router; 