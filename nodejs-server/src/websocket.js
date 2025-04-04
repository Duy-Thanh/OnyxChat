const { v4: uuidv4 } = require('uuid');
const jwt = require('jsonwebtoken');
const db = require('./models');

// Store clients connections
const clients = new Map();

// Authentication middleware for WebSocket
const authenticateWsConnection = (token) => {
  try {
    if (!token) {
      return null;
    }
    
    // Verify JWT token
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    return decoded.sub; // User ID from token
  } catch (error) {
    console.error('WebSocket authentication error:', error);
    return null;
  }
};

// Handle incoming messages
const handleMessage = async (userId, messageData) => {
  try {
    const { type, data } = JSON.parse(messageData);
    
    switch (type) {
      case 'MESSAGE':
        // Save message to database
        const message = await db.Message.create({
          senderId: userId,
          recipientId: data.recipientId,
          content: data.content,
          encrypted: data.encrypted || false,
          contentType: data.contentType || 'text',
        });
        
        // Forward message to recipient if online
        if (clients.has(data.recipientId)) {
          const client = clients.get(data.recipientId);
          if (client.readyState === 1) { // WebSocket.OPEN
            client.send(JSON.stringify({
              type: 'NEW_MESSAGE',
              data: {
                id: message.id,
                senderId: userId,
                content: data.content,
                encrypted: data.encrypted || false,
                contentType: data.contentType || 'text',
                timestamp: message.createdAt
              }
            }));
          }
        }
        break;
        
      case 'READ_RECEIPT':
        // Update message as read
        await db.Message.update(
          { read: true, readAt: new Date() },
          { where: { id: data.messageId, recipientId: userId }}
        );
        
        // Notify sender if online
        const readMessage = await db.Message.findByPk(data.messageId);
        if (readMessage && clients.has(readMessage.senderId)) {
          const client = clients.get(readMessage.senderId);
          if (client.readyState === 1) {
            client.send(JSON.stringify({
              type: 'READ_RECEIPT',
              data: {
                messageId: data.messageId,
                readAt: new Date()
              }
            }));
          }
        }
        break;
        
      case 'TYPING':
        // Notify recipient that user is typing
        if (clients.has(data.recipientId)) {
          const client = clients.get(data.recipientId);
          if (client.readyState === 1) {
            client.send(JSON.stringify({
              type: 'TYPING',
              data: {
                userId: userId,
                isTyping: data.isTyping
              }
            }));
          }
        }
        break;
        
      default:
        console.log('Unknown message type:', type);
    }
  } catch (error) {
    console.error('Error handling WebSocket message:', error);
  }
};

// Set up WebSocket server
const setupWebSocketServer = (wss) => {
  wss.on('connection', (ws, req) => {
    console.log('WebSocket client connected');
    const connectionId = uuidv4();
    
    // Extract token from URL params
    const url = new URL(req.url, 'ws://localhost');
    const token = url.searchParams.get('token');
    const userId = authenticateWsConnection(token);
    
    if (!userId) {
      // Close connection if authentication fails
      ws.close(4001, 'Unauthorized');
      return;
    }
    
    // Store client connection
    clients.set(userId, ws);
    ws.userId = userId;
    ws.connectionId = connectionId;
    
    // Update user as online
    db.User.update({ isActive: true, lastActiveAt: new Date() }, { where: { id: userId } })
      .catch(err => console.error('Failed to update user active status:', err));
    
    // Send confirmation that connection is established
    ws.send(JSON.stringify({
      type: 'CONNECTION_ESTABLISHED',
      data: { connectionId, userId }
    }));
    
    // Notify user's contacts that they're online
    notifyUserStatus(userId, true);
    
    // Handle incoming messages
    ws.on('message', (message) => {
      handleMessage(userId, message);
    });
    
    // Handle disconnection
    ws.on('close', () => {
      console.log('WebSocket client disconnected:', connectionId);
      
      // Update user as offline
      db.User.update({ isActive: false, lastActiveAt: new Date() }, { where: { id: userId } })
        .catch(err => console.error('Failed to update user inactive status:', err));
      
      // Remove from clients map
      clients.delete(userId);
      
      // Notify user's contacts that they're offline
      notifyUserStatus(userId, false);
    });
  });
  
  // Heartbeat to keep connections alive
  setInterval(() => {
    wss.clients.forEach((ws) => {
      if (ws.isAlive === false) {
        return ws.terminate();
      }
      
      ws.isAlive = false;
      ws.ping();
    });
  }, 30000);
};

// Notify user's contacts about online/offline status change
const notifyUserStatus = async (userId, isOnline) => {
  try {
    // Skip in mock mode if Contact model doesn't exist
    if (!db.Contact) {
      console.log('Contact model not available, skipping status notification');
      return;
    }
    
    // Find user's contacts
    const contacts = await db.Contact.findAll({
      where: { userId }
    });
    
    // Notify each online contact
    contacts.forEach(contact => {
      if (clients.has(contact.contactId)) {
        const client = clients.get(contact.contactId);
        if (client.readyState === 1) {
          client.send(JSON.stringify({
            type: 'USER_STATUS',
            data: {
              userId,
              isOnline,
              timestamp: new Date()
            }
          }));
        }
      }
    });
  } catch (error) {
    console.error('Error notifying user status:', error);
  }
};

module.exports = {
  setupWebSocketServer,
  clients
}; 