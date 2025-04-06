const express = require('express');
const { body, param, validationResult } = require('express-validator');
const { authenticate } = require('../middleware/auth.middleware');
const db = require('../models');
const { ValidationError, NotFoundError, ForbiddenError } = require('../utils/error.utils');

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
 * @route GET /api/friend-requests
 * @desc Get all friend requests for current user
 * @access Private
 */
router.get('/', authenticate, async (req, res, next) => {
  try {
    const userId = req.user.id;
    
    // Get friend requests received
    const received = await db.FriendRequest.findAll({
      where: { 
        receiverId: userId,
        status: 'pending'
      },
      include: [
        {
          model: db.User,
          as: 'sender',
          attributes: ['id', 'username', 'displayName', 'isActive', 'lastActiveAt']
        }
      ],
      order: [['createdAt', 'DESC']]
    });
    
    // Get friend requests sent
    const sent = await db.FriendRequest.findAll({
      where: { 
        senderId: userId,
        status: 'pending'
      },
      include: [
        {
          model: db.User,
          as: 'receiver',
          attributes: ['id', 'username', 'displayName', 'isActive', 'lastActiveAt']
        }
      ],
      order: [['createdAt', 'DESC']]
    });
    
    res.json({
      status: 'success',
      data: {
        received: received.map(request => ({
          id: request.id,
          sender: request.sender.toProfile(),
          message: request.message,
          status: request.status,
          createdAt: request.createdAt
        })),
        sent: sent.map(request => ({
          id: request.id,
          receiver: request.receiver.toProfile(),
          message: request.message,
          status: request.status,
          createdAt: request.createdAt
        }))
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route POST /api/friend-requests
 * @desc Send a friend request
 * @access Private
 */
router.post('/', authenticate, [
  body('receiverId')
    .isUUID()
    .withMessage('Invalid receiver ID'),
  body('message')
    .optional()
    .isString()
    .withMessage('Message must be a string'),
  validate
], async (req, res, next) => {
  try {
    const { receiverId, message } = req.body;
    const senderId = req.user.id;
    
    // Check that receiver exists
    const receiver = await db.User.findByPk(receiverId);
    if (!receiver) {
      return next(new NotFoundError('User'));
    }
    
    // Can't send request to yourself
    if (senderId === receiverId) {
      return next(new ValidationError('Cannot send friend request to yourself'));
    }
    
    // Check if users are already contacts
    const existingContact = await db.Contact.findOne({
      where: {
        userId: senderId,
        contactId: receiverId
      }
    });
    
    if (existingContact) {
      return next(new ValidationError('User is already in your contacts'));
    }
    
    // Check if there's already a pending request
    const existingRequest = await db.FriendRequest.findOne({
      where: {
        senderId,
        receiverId,
        status: 'pending'
      }
    });
    
    if (existingRequest) {
      return next(new ValidationError('Friend request already sent'));
    }
    
    // Check if there's a pending request from the receiver
    const reverseRequest = await db.FriendRequest.findOne({
      where: {
        senderId: receiverId,
        receiverId: senderId,
        status: 'pending'
      }
    });
    
    if (reverseRequest) {
      // If there's already a request from the receiver, auto-accept it
      await reverseRequest.update({ status: 'accepted' });
      
      // Create contact relationships both ways
      await db.Contact.create({
        userId: senderId,
        contactId: receiverId,
        nickname: null,
        blocked: false
      });
      
      await db.Contact.create({
        userId: receiverId,
        contactId: senderId,
        nickname: null,
        blocked: false
      });
      
      return res.status(201).json({
        status: 'success',
        message: 'Friend request from the user was accepted',
        data: {
          contact: receiver.toProfile()
        }
      });
    }
    
    // Create new friend request
    const friendRequest = await db.FriendRequest.create({
      senderId,
      receiverId,
      message: message || null,
      status: 'pending'
    });
    
    res.status(201).json({
      status: 'success',
      message: 'Friend request sent',
      data: {
        request: {
          id: friendRequest.id,
          receiver: receiver.toProfile(),
          message: friendRequest.message,
          status: friendRequest.status,
          createdAt: friendRequest.createdAt
        }
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route GET /api/friend-requests/users
 * @desc Get all users for friend discovery
 * @access Private
 */
router.get('/users', authenticate, async (req, res, next) => {
  try {
    const userId = req.user.id;
    
    // Get all users except current user
    const users = await db.User.findAll({
      where: {
        id: {
          [db.Sequelize.Op.ne]: userId
        }
      },
      attributes: ['id', 'username', 'displayName', 'isActive', 'lastActiveAt']
    });
    
    // Get contacts and pending requests to filter them out
    const contacts = await db.Contact.findAll({
      where: { userId },
      attributes: ['contactId']
    });
    
    const sentRequests = await db.FriendRequest.findAll({
      where: { 
        senderId: userId,
        status: 'pending'
      },
      attributes: ['receiverId']
    });
    
    const receivedRequests = await db.FriendRequest.findAll({
      where: { 
        receiverId: userId,
        status: 'pending'
      },
      attributes: ['senderId']
    });
    
    // Filter to create lists
    const contactIds = contacts.map(c => c.contactId);
    const sentRequestIds = sentRequests.map(r => r.receiverId);
    const receivedRequestIds = receivedRequests.map(r => r.senderId);
    
    // Categorize users
    const usersWithStatus = users.map(user => {
      const isContact = contactIds.includes(user.id);
      const hasSentRequest = sentRequestIds.includes(user.id);
      const hasReceivedRequest = receivedRequestIds.includes(user.id);
      
      let status = 'none';
      if (isContact) status = 'contact';
      else if (hasSentRequest) status = 'sent';
      else if (hasReceivedRequest) status = 'received';
      
      return {
        ...user.toProfile(),
        friendStatus: status
      };
    });
    
    res.json({
      status: 'success',
      data: {
        users: usersWithStatus
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route PUT /api/friend-requests/:id/accept
 * @desc Accept a friend request
 * @access Private
 */
router.put('/:id/accept', authenticate, [
  param('id')
    .isUUID()
    .withMessage('Invalid request ID'),
  validate
], async (req, res, next) => {
  try {
    const requestId = req.params.id;
    const userId = req.user.id;
    
    // Find the request
    const friendRequest = await db.FriendRequest.findOne({
      where: {
        id: requestId,
        receiverId: userId,
        status: 'pending'
      },
      include: [
        {
          model: db.User,
          as: 'sender',
          attributes: ['id', 'username', 'displayName', 'isActive', 'lastActiveAt']
        }
      ]
    });
    
    if (!friendRequest) {
      return next(new NotFoundError('Friend request'));
    }
    
    // Update request status
    await friendRequest.update({ status: 'accepted' });
    
    // Create contact relationships both ways
    await db.Contact.create({
      userId: userId,
      contactId: friendRequest.senderId,
      nickname: null,
      blocked: false
    });
    
    await db.Contact.create({
      userId: friendRequest.senderId,
      contactId: userId,
      nickname: null,
      blocked: false
    });
    
    res.json({
      status: 'success',
      message: 'Friend request accepted',
      data: {
        contact: friendRequest.sender.toProfile()
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route PUT /api/friend-requests/:id/reject
 * @desc Reject a friend request
 * @access Private
 */
router.put('/:id/reject', authenticate, [
  param('id')
    .isUUID()
    .withMessage('Invalid request ID'),
  validate
], async (req, res, next) => {
  try {
    const requestId = req.params.id;
    const userId = req.user.id;
    
    // Find the request
    const friendRequest = await db.FriendRequest.findOne({
      where: {
        id: requestId,
        receiverId: userId,
        status: 'pending'
      }
    });
    
    if (!friendRequest) {
      return next(new NotFoundError('Friend request'));
    }
    
    // Update request status
    await friendRequest.update({ status: 'rejected' });
    
    res.json({
      status: 'success',
      message: 'Friend request rejected'
    });
  } catch (error) {
    next(error);
  }
});

/**
 * @route DELETE /api/friend-requests/:id
 * @desc Cancel a sent friend request
 * @access Private
 */
router.delete('/:id', authenticate, [
  param('id')
    .isUUID()
    .withMessage('Invalid request ID'),
  validate
], async (req, res, next) => {
  try {
    const requestId = req.params.id;
    const userId = req.user.id;
    
    // Find the request
    const friendRequest = await db.FriendRequest.findOne({
      where: {
        id: requestId,
        senderId: userId,
        status: 'pending'
      }
    });
    
    if (!friendRequest) {
      return next(new NotFoundError('Friend request'));
    }
    
    // Delete the request
    await friendRequest.destroy();
    
    res.json({
      status: 'success',
      message: 'Friend request cancelled'
    });
  } catch (error) {
    next(error);
  }
});

module.exports = router; 