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
 * @route GET /api/messages
 * @desc Get user messages
 * @access Protected
 */
router.get('/', authenticate, async (req, res, next) => {
  try {
    // Get messages where user is either sender or recipient
    const messages = await db.Message.findAll({
      where: {
        [db.Sequelize.Op.or]: [
          { senderId: req.user.id },
          { recipientId: req.user.id }
        ]
      },
      order: [['createdAt', 'DESC']],
      limit: 100
    });
    
    res.json({
      status: 'success',
      data: {
        messages
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/messages/with/:userId
 * @desc Get messages exchanged with a specific user
 * @access Protected
 */
router.get('/with/:userId', authenticate, [
  param('userId').isUUID().withMessage('Invalid user ID'),
  validate
], async (req, res, next) => {
  try {
    const otherUserId = req.params.userId;
    
    // Check if the other user exists
    const otherUser = await db.User.findByPk(otherUserId);
    if (!otherUser) {
      return next(new NotFoundError('User'));
    }
    
    // Get messages between the current user and the specified user
    const messages = await db.Message.findAll({
      where: {
        [db.Sequelize.Op.or]: [
          { 
            senderId: req.user.id,
            recipientId: otherUserId
          },
          {
            senderId: otherUserId,
            recipientId: req.user.id
          }
        ]
      },
      order: [['createdAt', 'ASC']]
    });
    
    res.json({
      status: 'success',
      data: {
        messages,
        user: otherUser.toProfile ? otherUser.toProfile() : {
          id: otherUser.id,
          username: otherUser.username,
          displayName: otherUser.displayName,
          isActive: otherUser.isActive,
          lastActiveAt: otherUser.lastActiveAt
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/messages/:id
 * @desc Get message by ID
 * @access Protected
 */
router.get('/:id', authenticate, [
  param('id').isUUID().withMessage('Invalid message ID'),
  validate
], async (req, res, next) => {
  try {
    const message = await db.Message.findByPk(req.params.id);
    
    if (!message) {
      return next(new NotFoundError('Message'));
    }
    
    // Check if user is allowed to view this message
    if (message.senderId !== req.user.id && message.recipientId !== req.user.id) {
      return next(new ForbiddenError('You are not allowed to view this message'));
    }
    
    res.json({
      status: 'success',
      data: {
        message
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/messages
 * @desc Send a message
 * @access Protected
 */
router.post('/', authenticate, [
  body('recipientId').isUUID().withMessage('Invalid recipient ID'),
  body('content').notEmpty().withMessage('Content is required'),
  body('contentType').optional().isIn(['text', 'image', 'file', 'audio']).withMessage('Invalid content type'),
  body('encrypted').optional().isBoolean().withMessage('Encrypted must be a boolean'),
  validate
], async (req, res, next) => {
  try {
    const { recipientId, content, contentType = 'text', encrypted = true } = req.body;
    
    // Check if recipient exists
    const recipient = await db.User.findByPk(recipientId);
    if (!recipient) {
      return next(new NotFoundError('Recipient'));
    }
    
    // Create message
    const message = await db.Message.create({
      id: uuidv4(),
      senderId: req.user.id,
      recipientId,
      content,
      contentType,
      encrypted,
      sent: true,
      received: false,
      read: false,
      deleted: false
    });
    
    // Notify recipient via WebSocket if they're online
    const { clients } = require('../websocket');
    if (clients.has(recipientId)) {
      const client = clients.get(recipientId);
      if (client.readyState === 1) { // WebSocket.OPEN
        client.send(JSON.stringify({
          type: 'NEW_MESSAGE',
          data: {
            id: message.id,
            senderId: req.user.id,
            content: message.content,
            contentType: message.contentType,
            encrypted: message.encrypted,
            timestamp: message.createdAt
          }
        }));
      }
    }
    
    res.status(201).json({
      status: 'success',
      message: 'Message sent successfully',
      data: {
        message
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route PUT /api/messages/:id/received
 * @desc Mark message as received
 * @access Protected
 */
router.put('/:id/received', authenticate, [
  param('id').isUUID().withMessage('Invalid message ID'),
  validate
], async (req, res, next) => {
  try {
    const message = await db.Message.findByPk(req.params.id);
    
    if (!message) {
      return next(new NotFoundError('Message'));
    }
    
    // Check if user is the recipient
    if (message.recipientId !== req.user.id) {
      return next(new ForbiddenError('You can only mark messages sent to you as received'));
    }
    
    // Update message
    await message.update({
      received: true,
      receivedAt: new Date()
    });
    
    // Notify sender via WebSocket if they're online
    const { clients } = require('../websocket');
    if (clients.has(message.senderId)) {
      const client = clients.get(message.senderId);
      if (client.readyState === 1) { // WebSocket.OPEN
        client.send(JSON.stringify({
          type: 'RECEIVED_RECEIPT',
          data: {
            messageId: message.id,
            receivedAt: message.receivedAt
          }
        }));
      }
    }
    
    res.json({
      status: 'success',
      message: 'Message marked as received',
      data: {
        message
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route PUT /api/messages/:id/read
 * @desc Mark message as read
 * @access Protected
 */
router.put('/:id/read', authenticate, [
  param('id').isUUID().withMessage('Invalid message ID'),
  validate
], async (req, res, next) => {
  try {
    const message = await db.Message.findByPk(req.params.id);
    
    if (!message) {
      return next(new NotFoundError('Message'));
    }
    
    // Check if user is the recipient
    if (message.recipientId !== req.user.id) {
      return next(new ForbiddenError('You can only mark messages sent to you as read'));
    }
    
    // Update message
    await message.update({
      read: true,
      readAt: new Date(),
      // Also ensure it's marked as received
      received: true,
      receivedAt: message.receivedAt || new Date()
    });
    
    // Notify sender via WebSocket if they're online
    const { clients } = require('../websocket');
    if (clients.has(message.senderId)) {
      const client = clients.get(message.senderId);
      if (client.readyState === 1) { // WebSocket.OPEN
        client.send(JSON.stringify({
          type: 'READ_RECEIPT',
          data: {
            messageId: message.id,
            readAt: message.readAt
          }
        }));
      }
    }
    
    res.json({
      status: 'success',
      message: 'Message marked as read',
      data: {
        message
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route DELETE /api/messages/:id
 * @desc Delete message (soft delete)
 * @access Protected
 */
router.delete('/:id', authenticate, [
  param('id').isUUID().withMessage('Invalid message ID'),
  validate
], async (req, res, next) => {
  try {
    const message = await db.Message.findByPk(req.params.id);
    
    if (!message) {
      return next(new NotFoundError('Message'));
    }
    
    // Check if user is either sender or recipient
    if (message.senderId !== req.user.id && message.recipientId !== req.user.id) {
      return next(new ForbiddenError('You can only delete messages you sent or received'));
    }
    
    // Soft delete message
    await message.update({
      deleted: true,
      deletedAt: new Date()
    });
    
    res.json({
      status: 'success',
      message: 'Message deleted successfully'
    });
  } catch (error) {
    next(error);
  }
});

module.exports = router; 