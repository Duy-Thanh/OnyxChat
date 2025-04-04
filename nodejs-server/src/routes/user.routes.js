const express = require('express');
const { body, param, validationResult } = require('express-validator');
const { authenticate } = require('../middleware/auth.middleware');
const { 
  ValidationError, 
  NotFoundError, 
  ForbiddenError 
} = require('../utils/error.utils');
const { hashPassword } = require('../utils/auth.utils');
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
 * @route GET /api/users
 * @desc Get all users
 * @access Protected
 */
router.get('/', authenticate, async (req, res, next) => {
  try {
    const users = await db.User.findAll();
    
    res.json({
      status: 'success',
      data: {
        users: users.map(user => user.toProfile())
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/users/:id
 * @desc Get user by ID
 * @access Protected
 */
router.get('/:id', authenticate, [
  param('id').isUUID().withMessage('Invalid user ID'),
  validate
], async (req, res, next) => {
  try {
    const user = await db.User.findByPk(req.params.id);
    
    if (!user) {
      return next(new NotFoundError('User'));
    }
    
    res.json({
      status: 'success',
      data: {
        user: user.toProfile()
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route PUT /api/users/:id
 * @desc Update user
 * @access Protected
 */
router.put('/:id', authenticate, [
  param('id').isUUID().withMessage('Invalid user ID'),
  body('displayName')
    .optional()
    .isLength({ min: 2, max: 50 })
    .withMessage('Display name must be between 2 and 50 characters'),
  body('email')
    .optional()
    .isEmail()
    .withMessage('Please provide a valid email address'),
  body('password')
    .optional()
    .isLength({ min: 8 })
    .withMessage('Password must be at least 8 characters long'),
  body('currentPassword')
    .if(body('password').exists())
    .notEmpty()
    .withMessage('Current password is required to set a new password'),
  validate
], async (req, res, next) => {
  try {
    // Check if user exists
    const user = await db.User.findByPk(req.params.id);
    
    if (!user) {
      return next(new NotFoundError('User'));
    }
    
    // Check if current user is updating their own profile
    if (user.id !== req.user.id) {
      return next(new ForbiddenError('You can only update your own profile'));
    }
    
    const updateData = {};
    
    // Update display name if provided
    if (req.body.displayName) {
      updateData.displayName = req.body.displayName;
    }
    
    // Update email if provided
    if (req.body.email && req.body.email !== user.email) {
      // Check if email already exists
      const existingEmail = await db.User.findOne({ 
        where: { email: req.body.email }
      });
      
      if (existingEmail) {
        return next(new ValidationError('Email already in use'));
      }
      
      updateData.email = req.body.email;
    }
    
    // Update password if provided
    if (req.body.password) {
      // Verify current password
      const isMatch = await require('bcryptjs').compare(
        req.body.currentPassword, 
        user.passwordHash
      );
      
      if (!isMatch) {
        return next(new ValidationError('Current password is incorrect'));
      }
      
      updateData.passwordHash = await hashPassword(req.body.password);
    }
    
    // Update user
    if (Object.keys(updateData).length > 0) {
      await user.update(updateData);
    }
    
    res.json({
      status: 'success',
      message: 'User updated successfully',
      data: {
        user: user.toProfile()
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/users/:id/contacts
 * @desc Get user contacts
 * @access Protected
 */
router.get('/:id/contacts', authenticate, [
  param('id').isUUID().withMessage('Invalid user ID'),
  validate
], async (req, res, next) => {
  try {
    // Check if user exists
    const user = await db.User.findByPk(req.params.id);
    
    if (!user) {
      return next(new NotFoundError('User'));
    }
    
    // Check if current user is accessing their own contacts
    if (user.id !== req.user.id) {
      return next(new ForbiddenError('You can only access your own contacts'));
    }
    
    // Get user's contacts
    const contacts = await db.Contact.findAll({
      where: { userId: user.id },
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
      data: {
        contacts: contacts.map(contact => ({
          id: contact.id,
          user: {
            id: contact.contact.id,
            username: contact.contact.username,
            displayName: contact.contact.displayName,
            isActive: contact.contact.isActive,
            lastActiveAt: contact.contact.lastActiveAt
          },
          nickname: contact.nickname,
          blocked: contact.blocked
        }))
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/users/:id/contacts
 * @desc Add contact
 * @access Protected
 */
router.post('/:id/contacts', authenticate, [
  param('id').isUUID().withMessage('Invalid user ID'),
  body('contactId').isUUID().withMessage('Invalid contact ID'),
  body('nickname').optional().isString().withMessage('Nickname must be a string'),
  validate
], async (req, res, next) => {
  try {
    // Check if current user is adding to their own contacts
    if (req.params.id !== req.user.id) {
      return next(new ForbiddenError('You can only add to your own contacts'));
    }
    
    // Check if contact user exists
    const contactUser = await db.User.findByPk(req.body.contactId);
    
    if (!contactUser) {
      return next(new NotFoundError('Contact user'));
    }
    
    // Check if contact already exists
    const existingContact = await db.Contact.findOne({
      where: {
        userId: req.user.id,
        contactId: req.body.contactId
      }
    });
    
    if (existingContact) {
      return next(new ValidationError('Contact already exists'));
    }
    
    // Create contact
    const contact = await db.Contact.create({
      userId: req.user.id,
      contactId: req.body.contactId,
      nickname: req.body.nickname || null,
      blocked: false
    });
    
    res.status(201).json({
      status: 'success',
      message: 'Contact added successfully',
      data: {
        contact: {
          id: contact.id,
          user: contactUser.toProfile(),
          nickname: contact.nickname,
          blocked: contact.blocked
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route PUT /api/users/:userId/contacts/:contactId
 * @desc Update contact
 * @access Protected
 */
router.put('/:userId/contacts/:contactId', authenticate, [
  param('userId').isUUID().withMessage('Invalid user ID'),
  param('contactId').isUUID().withMessage('Invalid contact ID'),
  body('nickname').optional().isString().withMessage('Nickname must be a string'),
  body('blocked').optional().isBoolean().withMessage('Blocked must be a boolean'),
  validate
], async (req, res, next) => {
  try {
    // Check if current user is updating their own contact
    if (req.params.userId !== req.user.id) {
      return next(new ForbiddenError('You can only update your own contacts'));
    }
    
    // Find contact
    const contact = await db.Contact.findOne({
      where: {
        id: req.params.contactId,
        userId: req.user.id
      },
      include: [
        {
          model: db.User,
          as: 'contact',
          attributes: ['id', 'username', 'displayName', 'isActive', 'lastActiveAt']
        }
      ]
    });
    
    if (!contact) {
      return next(new NotFoundError('Contact'));
    }
    
    // Update contact
    const updateData = {};
    
    if (req.body.nickname !== undefined) {
      updateData.nickname = req.body.nickname;
    }
    
    if (req.body.blocked !== undefined) {
      updateData.blocked = req.body.blocked;
    }
    
    if (Object.keys(updateData).length > 0) {
      await contact.update(updateData);
    }
    
    res.json({
      status: 'success',
      message: 'Contact updated successfully',
      data: {
        contact: {
          id: contact.id,
          user: {
            id: contact.contact.id,
            username: contact.contact.username,
            displayName: contact.contact.displayName,
            isActive: contact.contact.isActive,
            lastActiveAt: contact.contact.lastActiveAt
          },
          nickname: contact.nickname,
          blocked: contact.blocked
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route DELETE /api/users/:userId/contacts/:contactId
 * @desc Delete contact
 * @access Protected
 */
router.delete('/:userId/contacts/:contactId', authenticate, [
  param('userId').isUUID().withMessage('Invalid user ID'),
  param('contactId').isUUID().withMessage('Invalid contact ID'),
  validate
], async (req, res, next) => {
  try {
    // Check if current user is deleting their own contact
    if (req.params.userId !== req.user.id) {
      return next(new ForbiddenError('You can only delete your own contacts'));
    }
    
    // Find contact
    const contact = await db.Contact.findOne({
      where: {
        id: req.params.contactId,
        userId: req.user.id
      }
    });
    
    if (!contact) {
      return next(new NotFoundError('Contact'));
    }
    
    // Delete contact
    await contact.destroy();
    
    res.json({
      status: 'success',
      message: 'Contact deleted successfully'
    });
  } catch (error) {
    next(error);
  }
});

module.exports = router; 