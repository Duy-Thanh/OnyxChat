const express = require('express');
const { body, param, validationResult } = require('express-validator');
const { authenticate } = require('../middleware/auth.middleware');
const { ValidationError, NotFoundError, ForbiddenError } = require('../utils/error.utils');
const db = require('../models');
const { v4: uuidv4 } = require('uuid');
// const { authJwt } = require("../middlewares");
const Op = db.Sequelize.Op;

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
    
    // Transform messages to include explicit timestamp field
    const messagesWithTimestamp = messages.map(message => {
      const messageObj = message.toJSON();
      return {
        ...messageObj,
        timestamp: message.createdAt.getTime(), // Add explicit timestamp in milliseconds
        formattedTime: message.createdAt.toISOString() // Add ISO formatted time
      };
    });
    
    res.json({
      status: 'success',
      data: {
        messages: messagesWithTimestamp
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
    
    // Transform messages to include explicit timestamp field
    const messagesWithTimestamp = messages.map(message => {
      const messageObj = message.toJSON();
      return {
        ...messageObj,
        timestamp: message.createdAt.getTime(), // Add explicit timestamp in milliseconds
        formattedTime: message.createdAt.toISOString() // Add ISO formatted time
      };
    });
    
    res.json({
      status: 'success',
      data: {
        messages: messagesWithTimestamp,
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
    
    // Add explicit timestamp fields
    const messageWithTimestamp = {
      ...message.toJSON(),
      timestamp: message.createdAt.getTime(), // Add explicit timestamp in milliseconds
      formattedTime: message.createdAt.toISOString() // Add ISO formatted time
    };
    
    res.json({
      status: 'success',
      data: {
        message: messageWithTimestamp
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
            timestamp: message.createdAt.getTime(), // Explicit timestamp in milliseconds
            createdAt: message.createdAt.toISOString() // ISO formatted time
          }
        }));
      }
    }
    
    res.status(201).json({
      status: 'success',
      message: 'Message sent successfully',
      data: {
        message: {
          ...message.toJSON(),
          timestamp: message.createdAt.getTime(), // Add explicit timestamp in milliseconds
          formattedTime: message.createdAt.toISOString() // Add ISO formatted time
        }
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

/**
 * Get messages between the current user and another user
 * @route GET /api/messages/:userId
 */
router.get("/:userId", [authenticate], async (req, res) => {
  try {
    const otherUserId = req.params.userId;
    
    if (!otherUserId) {
      return res.status(400).send({
        message: "User ID is required!"
      });
    }
    
    // Find messages where the current user is either the sender or recipient
    // and the other user is the opposite role
    const messages = await db.Message.findAll({
      where: {
        [Op.or]: [
          {
            senderId: req.userId,
            recipientId: otherUserId
          },
          {
            senderId: otherUserId,
            recipientId: req.userId
          }
        ]
      },
      order: [['createdAt', 'ASC']]
    });
    
    // Transform messages to include explicit timestamp fields
    const transformedMessages = messages.map(message => {
      const messageObj = message.toJSON();
      // Add explicit timestamp in milliseconds for client-side consistency
      messageObj.timestamp = messageObj.createdAt.getTime();
      // Add formatted time string for debugging and display
      messageObj.formattedTime = messageObj.createdAt.toISOString();
      return messageObj;
    });
    
    res.status(200).send(transformedMessages);
  } catch (err) {
    console.error("Error retrieving messages:", err);
    res.status(500).send({
      message: err.message || "Some error occurred while retrieving messages."
    });
  }
});

/**
 * Get conversations list for the current user
 * @route GET /api/messages/conversations/list
 */
router.get("/conversations/list", [authenticate], async (req, res) => {
  try {
    // Get the latest message with each user the current user has communicated with
    const userId = req.user.id;
    const conversations = await db.sequelize.query(`
      WITH latest_messages AS (
        SELECT 
          CASE 
            WHEN "senderId" = :userId THEN "recipientId" 
            ELSE "senderId" 
          END as other_user_id,
          MAX("createdAt") as latest_message_time
        FROM messages
        WHERE "senderId" = :userId OR "recipientId" = :userId
        GROUP BY other_user_id
      )
      SELECT 
        u.id as user_id,
        u.username,
        u."displayName" as display_name,
        u.email,
        m.id as message_id,
        m.content,
        m."senderId",
        m."recipientId",
        m."createdAt",
        m.read,
        CASE WHEN m.read = false AND m."recipientId" = :userId THEN true ELSE false END as unread
      FROM latest_messages lm
      JOIN users u ON u.id = lm.other_user_id
      JOIN messages m ON (
        (m."senderId" = :userId AND m."recipientId" = lm.other_user_id) OR
        (m."senderId" = lm.other_user_id AND m."recipientId" = :userId)
      ) AND m."createdAt" = lm.latest_message_time
      ORDER BY m."createdAt" DESC
    `, {
      replacements: { userId },
      type: db.sequelize.QueryTypes.SELECT
    });
    
    // Get unread counts for each conversation
    const unreadCounts = await db.sequelize.query(`
      SELECT 
        "senderId",
        COUNT(*) as unread_count
      FROM messages
      WHERE "recipientId" = :userId AND read = false
      GROUP BY "senderId"
    `, {
      replacements: { userId },
      type: db.sequelize.QueryTypes.SELECT
    });
    
    // Create a map of sender_id to unread_count
    const unreadCountMap = {};
    unreadCounts.forEach(count => {
      unreadCountMap[count.senderId] = count.unread_count;
    });
    
    // Enhance the conversations with unread counts and explicit timestamps
    const enhancedConversations = conversations.map(conv => {
      // Add timestamp fields for consistent time display in the app
      let timestamp;
      let formattedTime;
      
      if (conv.createdAt) {
        const date = new Date(conv.createdAt);
        timestamp = date.getTime();
        formattedTime = date.toISOString();
      } else {
        timestamp = Date.now();
        formattedTime = new Date().toISOString();
      }
      
      return {
        ...conv,
        unread_count: unreadCountMap[conv.user_id] || 0,
        timestamp,
        formattedTime
      };
    });
    
    res.status(200).send(enhancedConversations);
  } catch (err) {
    console.error("Error retrieving conversations:", err);
    res.status(500).send({
      message: err.message || "Some error occurred while retrieving conversations."
    });
  }
});

/**
 * Get messages with a user by email address
 * @route GET /api/messages/email/:email
 * @access Protected
 */
router.get("/email/:email", authenticate, async (req, res) => {
  try {
    const email = req.params.email;
    // Remove .onion suffix if present
    const cleanEmail = email.endsWith('.onion') ? email.substring(0, email.length - 6) : email;
    
    // Find the user by email
    const otherUser = await db.User.findOne({
      where: { email: cleanEmail }
    });
    
    if (!otherUser) {
      return res.status(404).send({
        message: "User not found with email: " + cleanEmail
      });
    }
    
    // Find messages between current user and the other user
    const messages = await db.Message.findAll({
      where: {
        [Op.or]: [
          {
            senderId: req.user.id,
            recipientId: otherUser.id
          },
          {
            senderId: otherUser.id,
            recipientId: req.user.id
          }
        ]
      },
      order: [['createdAt', 'ASC']]
    });
    
    // Transform messages to include explicit timestamp fields
    const transformedMessages = messages.map(message => {
      const messageObj = message.toJSON();
      // Add explicit timestamp in milliseconds for client-side consistency
      messageObj.timestamp = messageObj.createdAt.getTime();
      // Add formatted time string for debugging and display
      messageObj.formattedTime = messageObj.createdAt.toISOString();
      return messageObj;
    });
    
    res.status(200).send(transformedMessages);
  } catch (err) {
    console.error("Error retrieving messages by email:", err);
    res.status(500).send({
      message: err.message || "Some error occurred while retrieving messages."
    });
  }
});

/**
 * Create a new message
 * @route POST /api/messages
 */
router.post("/", [authenticate], async (req, res) => {
  try {
    if (!req.body.recipientId || !req.body.content) {
      return res.status(400).send({
        message: "Content and recipientId are required!"
      });
    }

    // Check if content is JSON for media messages
    let contentType = req.body.contentType || 'text';
    let content = req.body.content;
    let isMediaContent = false;

    // Try to parse as JSON for media content
    try {
      const contentObj = JSON.parse(content);
      if (contentObj.type && contentObj.url) {
        // This is a media message
        isMediaContent = true;
        contentType = contentObj.type.toLowerCase();
      }
    } catch (e) {
      // Not JSON, assume regular text
    }

    // Create the message
    const newMessage = await db.Message.create({
      senderId: req.userId,
      recipientId: req.body.recipientId,
      content: content,
      encrypted: req.body.encrypted || false,
      contentType: contentType,
    });
    
    // Create response with explicit timestamp fields
    const messageResponse = {
      id: newMessage.id,
      senderId: newMessage.senderId,
      recipientId: newMessage.recipientId,
      content: newMessage.content,
      encrypted: newMessage.encrypted,
      contentType: newMessage.contentType,
      createdAt: newMessage.createdAt,
      // Add explicit timestamp in milliseconds for client-side consistency
      timestamp: newMessage.createdAt.getTime(),
      // Add formatted time string for debugging and display
      formattedTime: newMessage.createdAt.toISOString()
    };

    // If the recipient is online, send them a message via WebSocket
    const recipientSocketId = onlineUsers[req.body.recipientId];
    if (recipientSocketId) {
      // Send message via WebSocket
      io.to(recipientSocketId).emit('message', {
        type: 'message',
        senderId: req.userId,
        recipientId: req.body.recipientId,
        content: content,
        encrypted: req.body.encrypted || false,
        contentType: contentType,
        timestamp: newMessage.createdAt.getTime(),
        formattedTime: newMessage.createdAt.toISOString()
      });
    }

    res.status(201).send(messageResponse);
  } catch (err) {
    console.error("Error creating message:", err);
    res.status(500).send({
      message: err.message || "Some error occurred while creating the message."
    });
  }
});

/**
 * Mark a message as read
 * @route PUT /api/messages/:id/read
 */
router.put("/:id/read", [authenticate], async (req, res) => {
  try {
    const messageId = req.params.id;
    
    // Verify the message exists and is sent to this user
    const message = await db.Message.findOne({
      where: {
        id: messageId,
        recipientId: req.userId
      }
    });
    
    if (!message) {
      return res.status(404).send({
        message: "Message not found or not addressed to you."
      });
    }
    
    // Update the message
    await message.update({
      read: true,
      readAt: new Date()
    });
    
    // Create response with explicit timestamp fields
    const response = {
      message: "Message marked as read successfully.",
      id: message.id,
      read: true,
      readAt: message.readAt,
      timestamp: message.readAt.getTime(),
      formattedTime: message.readAt.toISOString()
    };
    
    res.status(200).send(response);
  } catch (err) {
    console.error("Error marking message as read:", err);
    res.status(500).send({
      message: err.message || "Some error occurred while updating the message."
    });
  }
});

module.exports = router; 