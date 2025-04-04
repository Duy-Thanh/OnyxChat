const { v4: uuidv4 } = require('uuid');
const jwt = require('jsonwebtoken');
const db = require('./models');

// Store clients connections
const clients = new Map();

// Authentication middleware for WebSocket
const authenticateWsConnection = (token) => {
  try {
    if (!token) {
      console.log('No token provided for WebSocket authentication');
      return null;
    }

    console.log('Authenticating WebSocket with token:', token.substring(0, 10) + '...');
    
    // Verify JWT token
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    console.log('WebSocket token verified successfully for user:', decoded.sub);
    return decoded.sub; // User ID from token
  } catch (error) {
    console.error('WebSocket authentication error:', error);
    
    // Check if this is a token expiration error
    if (error.name === 'TokenExpiredError') {
      // Instead of returning null, return a special string indicating token expired
      console.log('Token expired, client should refresh token');
      return 'TOKEN_EXPIRED';
    }
    
    return null;
  }
};

// Handle incoming messages
const handleMessage = async (userId, messageData) => {
  try {
    const message = JSON.parse(messageData);
    const { type, data } = message;
    
    switch (type) {
      case 'ping':
        // Respond to ping with pong to keep connection alive
        const client = clients.get(userId);
        if (client && client.readyState === 1) { // WebSocket.OPEN
          client.send(JSON.stringify({
            type: 'pong',
            timestamp: new Date().getTime()
          }));
        }
        break;
        
      case 'MESSAGE':
        // Save message to database
        const newMessage = await db.Message.create({
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
                id: newMessage.id,
                senderId: userId,
                content: data.content,
                encrypted: data.encrypted || false,
                contentType: data.contentType || 'text',
                timestamp: newMessage.createdAt
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
  console.log('Setting up WebSocket server on path: ' + (wss.options.path || 'default'));
  
  // Handle upgrade requests explicitly
  wss.on('headers', (headers, request) => {
    console.log('WebSocket upgrade request headers:', headers);
  });
  
  wss.on('connection', (ws, req) => {
    console.log('WebSocket client connected');
    console.log('Connection URL:', req.url);
    console.log('Connection headers:', JSON.stringify(req.headers));
    
    const connectionId = uuidv4();
    
    // First try to extract token from Authorization header
    let token = null;
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      token = authHeader.substring(7);
      console.log('Found token in Authorization header:', token.substring(0, 10) + '...');
    }
    
    // If no token in header, try URL params as fallback
    if (!token) {
      try {
        // Parse the URL even if it's partial
        const parsedUrl = new URL(req.url, 'ws://localhost');
        token = parsedUrl.searchParams.get('token');
        if (token) {
          console.log('Found token in URL parameters:', token.substring(0, 10) + '...');
        }
      } catch (e) {
        console.error('Error parsing WebSocket URL for token:', e.message);
        console.error('Original URL:', req.url);
      }
    }
    
    // If still no token, check custom header as last resort
    if (!token) {
      const customToken = req.headers['x-auth-token'];
      if (customToken) {
        token = customToken;
        console.log('Found token in custom header:', token.substring(0, 10) + '...');
      }
    }
    
    // If still no token, wait for an auth message
    if (!token) {
      console.log('No token found in connection, waiting for auth message');
      
      // Set up a one-time handler for the auth message
      const authHandler = (message) => {
        try {
          const parsedMessage = JSON.parse(message);
          if (parsedMessage.type === 'auth' && parsedMessage.token) {
            token = parsedMessage.token;
            console.log('Received token in auth message:', token.substring(0, 10) + '...');
            
            // Authenticate and proceed
            const userId = authenticateWsConnection(token);
            if (userId) {
              console.log(`WebSocket authenticated for user: ${userId}`);
              setupAuthenticatedConnection(ws, userId, connectionId);
            } else {
              console.log('WebSocket authentication failed via message, closing connection');
              ws.close(4001, 'Unauthorized');
            }
            
            // Remove this handler
            ws.removeEventListener('message', authHandler);
          }
        } catch (error) {
          console.error('Error processing auth message:', error);
        }
      };
      
      // Add the auth handler
      ws.on('message', authHandler);
      
      // Set a timeout for authentication
      setTimeout(() => {
        if (!ws.userId) {  // If not authenticated yet
          console.log('Authentication timeout, closing connection');
          ws.close(4001, 'Authentication timeout');
        }
      }, 10000);  // 10 seconds timeout
      
      return;
    }
    
    const userId = authenticateWsConnection(token);
    
    if (!userId) {
      // Close connection if authentication fails
      console.log('WebSocket authentication failed, closing connection');
      ws.close(4001, 'Unauthorized');
      return;
    } else if (userId === 'TOKEN_EXPIRED') {
      // Send token refresh request instead of closing connection
      console.log('Sending token refresh request to client');
      ws.send(JSON.stringify({
        type: 'TOKEN_REFRESH_REQUIRED',
        data: {
          message: 'Your authentication token has expired. Please refresh your token and reconnect.'
        }
      }));
      
      // Add a short timeout before closing to allow client to receive the message
      setTimeout(() => {
        ws.close(4003, 'Token expired');
      }, 1000);
      
      return;
    }
    
    setupAuthenticatedConnection(ws, userId, connectionId);
  });
  
  // Heartbeat to keep connections alive
  setInterval(() => {
    wss.clients.forEach((ws) => {
      if (ws.isAlive === false) {
        console.log(`Terminating inactive WebSocket connection: ${ws.connectionId || 'unknown'} for user: ${ws.userId || 'unknown'} - No pong received`);
        return ws.terminate();
      }
      
      ws.isAlive = false;
      ws.ping();
    });
  }, 30000);
};

// Setup authenticated connection
const setupAuthenticatedConnection = (ws, userId, connectionId) => {
  console.log(`WebSocket authenticated for user: ${userId}`);
  
  // Store client connection
  clients.set(userId, ws);
  ws.userId = userId;
  ws.connectionId = connectionId;
  ws.isAlive = true; // Initialize isAlive flag
  
  // Handle pong messages
  ws.on('pong', () => {
    ws.isAlive = true; // Reset the isAlive flag when pong is received
    console.log(`Received pong from user: ${userId}, connection: ${connectionId}`);
  });
  
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