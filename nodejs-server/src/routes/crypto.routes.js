const express = require('express');
const { body, param, validationResult } = require('express-validator');
const { authenticate } = require('../middleware/auth.middleware');
const { ValidationError, NotFoundError, ForbiddenError } = require('../utils/error.utils');
const db = require('../models');
const { v4: uuidv4 } = require('uuid');

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
 * @route POST /api/crypto/keys
 * @desc Register user's encryption keys
 * @access Protected
 */
router.post('/keys', authenticate, [
  body('identityKey').notEmpty().withMessage('Identity key is required'),
  body('signedPrekey').notEmpty().withMessage('Signed prekey is required'),
  body('signedPrekeySignature').notEmpty().withMessage('Signed prekey signature is required'),
  body('signedPrekeyId').isInt().withMessage('Signed prekey ID must be an integer'),
  validate
], async (req, res, next) => {
  try {
    const { identityKey, signedPrekey, signedPrekeySignature, signedPrekeyId } = req.body;
    
    // Check if user already has keys
    const existingKeys = await db.UserKey.findOne({
      where: { userId: req.user.id }
    });
    
    let userKey;
    
    if (existingKeys) {
      // Update existing keys
      userKey = await existingKeys.update({
        identityKey,
        signedPrekey,
        signedPrekeySignature,
        signedPrekeyId
      });
    } else {
      // Create new keys
      userKey = await db.UserKey.create({
        userId: req.user.id,
        identityKey,
        signedPrekey,
        signedPrekeySignature,
        signedPrekeyId
      });
    }
    
    res.status(existingKeys ? 200 : 201).json({
      status: 'success',
      message: existingKeys ? 'Keys updated successfully' : 'Keys registered successfully',
      data: {
        userKey: {
          userId: userKey.userId,
          identityKey: userKey.identityKey,
          signedPrekey: userKey.signedPrekey,
          signedPrekeySignature: userKey.signedPrekeySignature,
          signedPrekeyId: userKey.signedPrekeyId
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/crypto/keys/:userId
 * @desc Get user's encryption keys
 * @access Protected
 */
router.get('/keys/:userId', authenticate, [
  param('userId').isUUID().withMessage('Invalid user ID'),
  validate
], async (req, res, next) => {
  try {
    // Check if requested user exists
    const user = await db.User.findByPk(req.params.userId);
    if (!user) {
      return next(new NotFoundError('User'));
    }
    
    // Get user's keys
    const userKey = await db.UserKey.findOne({
      where: { userId: req.params.userId }
    });
    
    if (!userKey) {
      return next(new NotFoundError('User keys'));
    }
    
    res.json({
      status: 'success',
      data: {
        userKey: {
          userId: userKey.userId,
          identityKey: userKey.identityKey,
          signedPrekey: userKey.signedPrekey,
          signedPrekeySignature: userKey.signedPrekeySignature,
          signedPrekeyId: userKey.signedPrekeyId
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/crypto/prekeys
 * @desc Register one-time prekeys
 * @access Protected
 */
router.post('/prekeys', authenticate, [
  body('prekeys').isArray().withMessage('Prekeys must be an array'),
  body('prekeys.*.prekeyId').isInt().withMessage('Prekey ID must be an integer'),
  body('prekeys.*.prekey').notEmpty().withMessage('Prekey is required'),
  validate
], async (req, res, next) => {
  try {
    const { prekeys } = req.body;
    
    // Process each prekey
    const savedPrekeys = [];
    
    for (const prekey of prekeys) {
      // Check if prekey with this ID already exists
      const existingPrekey = await db.OneTimePreKey.findOne({
        where: {
          userId: req.user.id,
          prekeyId: prekey.prekeyId
        }
      });
      
      if (existingPrekey) {
        // Skip if already exists and not used
        if (!existingPrekey.used) {
          savedPrekeys.push(existingPrekey);
          continue;
        }
        
        // Update if used
        await existingPrekey.update({
          prekey: prekey.prekey,
          used: false,
          usedAt: null
        });
        
        savedPrekeys.push(existingPrekey);
      } else {
        // Create new prekey
        const newPrekey = await db.OneTimePreKey.create({
          userId: req.user.id,
          prekeyId: prekey.prekeyId,
          prekey: prekey.prekey,
          used: false
        });
        
        savedPrekeys.push(newPrekey);
      }
    }
    
    res.status(201).json({
      status: 'success',
      message: 'Prekeys registered successfully',
      data: {
        prekeys: savedPrekeys.map(pk => ({
          id: pk.id,
          prekeyId: pk.prekeyId,
          used: pk.used
        }))
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/crypto/prekeys/:userId
 * @desc Get unused one-time prekey for a user
 * @access Protected
 */
router.get('/prekeys/:userId', authenticate, [
  param('userId').isUUID().withMessage('Invalid user ID'),
  validate
], async (req, res, next) => {
  try {
    // Check if requested user exists
    const user = await db.User.findByPk(req.params.userId);
    if (!user) {
      return next(new NotFoundError('User'));
    }
    
    // Find unused prekey
    const prekey = await db.OneTimePreKey.findOne({
      where: {
        userId: req.params.userId,
        used: false
      }
    });
    
    if (!prekey) {
      return next(new NotFoundError('No unused prekeys available for this user'));
    }
    
    // Mark prekey as used
    await prekey.update({
      used: true,
      usedAt: new Date()
    });
    
    res.json({
      status: 'success',
      data: {
        prekey: {
          prekeyId: prekey.prekeyId,
          prekey: prekey.prekey
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/crypto/sessions
 * @desc Create or update encryption session
 * @access Protected
 */
router.post('/sessions', authenticate, [
  body('otherUserId').isUUID().withMessage('Invalid other user ID'),
  body('sessionData').notEmpty().withMessage('Session data is required'),
  validate
], async (req, res, next) => {
  try {
    const { otherUserId, sessionData } = req.body;
    
    // Check if other user exists
    const otherUser = await db.User.findByPk(otherUserId);
    if (!otherUser) {
      return next(new NotFoundError('User'));
    }
    
    // Check if session already exists
    const existingSession = await db.Session.findOne({
      where: {
        userId: req.user.id,
        otherUserId
      }
    });
    
    let session;
    
    if (existingSession) {
      // Update existing session
      session = await existingSession.update({
        sessionData
      });
    } else {
      // Create new session
      session = await db.Session.create({
        userId: req.user.id,
        otherUserId,
        sessionData
      });
    }
    
    res.status(existingSession ? 200 : 201).json({
      status: 'success',
      message: existingSession ? 'Session updated successfully' : 'Session created successfully',
      data: {
        session: {
          id: session.id,
          userId: session.userId,
          otherUserId: session.otherUserId,
          createdAt: session.createdAt,
          updatedAt: session.updatedAt
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/crypto/sessions/:otherUserId
 * @desc Get encryption session with another user
 * @access Protected
 */
router.get('/sessions/:otherUserId', authenticate, [
  param('otherUserId').isUUID().withMessage('Invalid other user ID'),
  validate
], async (req, res, next) => {
  try {
    // Check if session exists
    const session = await db.Session.findOne({
      where: {
        userId: req.user.id,
        otherUserId: req.params.otherUserId
      }
    });
    
    if (!session) {
      return next(new NotFoundError('Session'));
    }
    
    res.json({
      status: 'success',
      data: {
        session: {
          id: session.id,
          userId: session.userId,
          otherUserId: session.otherUserId,
          sessionData: session.sessionData,
          createdAt: session.createdAt,
          updatedAt: session.updatedAt
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

module.exports = router; 