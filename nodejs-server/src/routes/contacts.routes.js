const express = require('express');
const { body, validationResult } = require('express-validator');
const { authenticate } = require('../middleware/auth.middleware');
const db = require('../models');
const { ValidationError } = require('../utils/error.utils');

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
 * @route POST /api/contacts/sync
 * @desc Sync contacts to find which ones are app users
 * @access Private
 */
router.post('/sync', authenticate, [
  body('contacts')
    .isArray()
    .withMessage('Contacts must be an array of addresses'),
  validate
], async (req, res, next) => {
  try {
    const { contacts } = req.body;
    const userId = req.user.id;
    
    console.log(`User ${userId} is syncing ${contacts.length} contacts`);
    
    // Initialize result array
    let appUsers = [];
    
    // In mock mode, simply return a random subset of contacts as app users
    if (process.env.USE_MOCK_DB === 'true' || process.env.NODE_ENV === 'test') {
      console.log('Using mock mode for contact sync');
      // Randomly select ~70% of the contacts
      appUsers = contacts.filter(() => Math.random() > 0.3);
      console.log(`Mock mode: Found ${appUsers.length} app users out of ${contacts.length} contacts`);
    } else {
      // Real implementation: Query the database to find which contacts are registered users
      // Use username or email field depending on what client is sending as contact addresses
      const users = await db.User.findAll({
        where: {
          [db.Sequelize.Op.or]: [
            { username: contacts },
            { email: contacts }
          ]
        },
        attributes: ['username', 'email']
      });
      
      // Extract usernames and emails
      users.forEach(user => {
        if (contacts.includes(user.username)) {
          appUsers.push(user.username);
        }
        if (contacts.includes(user.email)) {
          appUsers.push(user.email);
        }
      });
      
      console.log(`Found ${appUsers.length} app users out of ${contacts.length} contacts`);
    }
    
    res.json({
      status: 'success',
      message: 'Contacts synced successfully',
      data: {
        appUsers
      }
    });
    
  } catch (error) {
    console.error('Error in contact sync:', error);
    next(error);
  }
});

/**
 * @route POST /api/contacts
 * @desc Add a new contact
 * @access Private
 */
router.post('/', authenticate, [
  body('contactId')
    .notEmpty()
    .withMessage('Contact ID is required'),
  body('nickname')
    .optional()
    .isString()
    .withMessage('Nickname must be a string'),
  validate
], async (req, res, next) => {
  try {
    const { contactId, nickname } = req.body;
    const userId = req.user.id;
    
    // Check if contact exists
    const contactUser = await db.User.findByPk(contactId);
    if (!contactUser) {
      return res.status(404).json({
        status: 'error',
        message: 'User not found'
      });
    }
    
    // Check if contact already exists
    const existingContact = await db.Contact.findOne({
      where: { userId, contactId }
    });
    
    if (existingContact) {
      return res.status(409).json({
        status: 'error',
        message: 'Contact already exists'
      });
    }
    
    // Create contact
    const contact = await db.Contact.create({
      userId,
      contactId,
      nickname,
      blocked: false
    });
    
    res.status(201).json({
      status: 'success',
      message: 'Contact added successfully',
      data: { contact }
    });
    
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/contacts
 * @desc Get all contacts for a user
 * @access Private
 */
router.get('/', authenticate, async (req, res, next) => {
  try {
    const userId = req.user.id;
    
    // Get all contacts
    const contacts = await db.Contact.findAll({
      where: { userId },
      include: [
        {
          model: db.User,
          as: 'contact',
          attributes: ['id', 'username', 'displayName', 'isActive', 'lastActiveAt']
        }
      ]
    });
    
    res.json({
      status: 'success',
      data: { contacts }
    });
    
  } catch (error) {
    next(error);
  }
});

module.exports = router; 