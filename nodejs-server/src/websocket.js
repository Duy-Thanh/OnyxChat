const { v4: uuidv4 } = require('uuid');
const jwt = require('jsonwebtoken');
const db = require('./models');

// Store clients connections - modified to support multiple connections per user
const clients = new Map();

// Store active calls and their timeouts
const activeCalls = new Map();
const CALL_TIMEOUT = 30000; // 30 seconds timeout for unanswered calls

// Call statistics tracking
const callStats = {
  totalCalls: 0,
  successfulCalls: 0,
  missedCalls: 0,
  averageDuration: 0,
  totalDuration: 0
};

// Authentication middleware for WebSocket
const authenticateWsConnection = (token) => {
  try {
    if (!token) {
      console.log('No token provided for WebSocket authentication');
      return null;
    }

    console.log('Authenticating WebSocket with token:', token.substring(0, 10) + '...');
    
    // Verify JWT token
    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      console.log('WebSocket token verified successfully for user:', decoded.sub);
      
      // Check if this is an access token (by type if available)
      if (decoded.type && decoded.type !== 'access') {
        console.warn(`WebSocket received wrong token type: ${decoded.type}, expected 'access'`);
        return 'INVALID_TOKEN_TYPE';
      }
      
      return decoded.sub; // User ID from token
    } catch (error) {
      // Handle specific JWT errors with more detailed responses
      if (error instanceof jwt.TokenExpiredError) {
        console.log(`WebSocket token expired at ${new Date(error.expiredAt)}, client should refresh token`);
        return 'TOKEN_EXPIRED';
      } else if (error instanceof jwt.JsonWebTokenError) {
        console.warn(`WebSocket invalid token: ${error.message}`);
        return 'INVALID_TOKEN';
      }
      
      console.error('WebSocket authentication error:', error);
      return null;
    }
  } catch (error) {
    console.error('WebSocket authentication error:', error);
    return null;
  }
};

// Handle incoming messages
const handleMessage = async (userId, messageData, connectionId) => {
  try {
    const message = JSON.parse(messageData);
    const { type, data } = message;
    
    switch (type) {
      case 'ping':
        // Respond to ping with pong to keep connection alive
        const userConnections = clients.get(userId);
        if (userConnections) {
          // Find the specific connection that sent this ping
          const connection = userConnections.find(conn => conn.connectionId === connectionId);
          if (connection && connection.ws.readyState === 1) { // WebSocket.OPEN
            connection.ws.send(JSON.stringify({
              type: 'pong',
              timestamp: new Date().getTime()
            }));
          }
        }
        break;
        
      case 'direct':
        // Handle direct messages from older client versions
        // Extract recipient and content properties directly from message
        let recipientEmail = message.recipient; // This could be an email or username, not a UUID
        const content = message.content;
        
        if (!recipientEmail || !content) {
          console.log('Invalid direct message format:', message);
          break;
        }
        
        console.log(`Processing direct message to recipient: ${recipientEmail}`);
        
        // Special handling for .onion addresses which aren't in our database
        if (recipientEmail.endsWith('.onion')) {
          // Remove the .onion suffix for database lookup
          recipientEmail = recipientEmail.replace('.onion', '');
          console.log(`Removed .onion suffix for lookup, searching for: ${recipientEmail}`);
        }
        
        // Look up the user ID by email or username before creating the message
        let recipientUser;
        try {
          // Try to find user by email first
          recipientUser = await db.User.findOne({
            where: {
              email: recipientEmail
            }
          });
          
          // If not found by email, try username
          if (!recipientUser) {
            recipientUser = await db.User.findOne({
              where: {
                username: recipientEmail
              }
            });
          }
          
          if (!recipientUser) {
            console.log(`Recipient not found in database: ${recipientEmail}`);
            
            // Send error response to sender
            const senderConnections = clients.get(userId);
            if (senderConnections) {
              const senderConnection = senderConnections.find(conn => conn.connectionId === connectionId);
              if (senderConnection && senderConnection.ws.readyState === 1) {
                senderConnection.ws.send(JSON.stringify({
                  type: 'ERROR',
                  data: {
                    message: `User with email or username "${recipientEmail}" not found.`,
                    code: 'USER_NOT_FOUND',
                    originalMessage: {
                      type: 'direct',
                      recipient: message.recipient,
                      content: content.substring(0, 20) + (content.length > 20 ? '...' : '')
                    }
                  }
                }));
              }
            }
            break;
          }
          
          console.log(`Found recipient user: ${recipientUser.username} (${recipientUser.id})`);
          
          // Now create the message with the proper UUID
          const directMessage = await db.Message.create({
            senderId: userId,
            recipientId: recipientUser.id, // Use the UUID from the database
            content: content,
            encrypted: false,
            contentType: 'text',
          });
          
          console.log(`Created message from ${userId} to ${recipientUser.id}: ${directMessage.id}`);
          
          // Forward message to all recipient's devices if online
          if (clients.has(recipientUser.id)) {
            const recipientConnections = clients.get(recipientUser.id);
            recipientConnections.forEach(connection => {
              if (connection.ws.readyState === 1) { // WebSocket.OPEN
                connection.ws.send(JSON.stringify({
                  type: 'NEW_MESSAGE',
                  data: {
                    id: directMessage.id,
                    senderId: userId,
                    content: content,
                    encrypted: false,
                    contentType: 'text',
                    timestamp: directMessage.createdAt
                  }
                }));
              }
            });
            console.log(`Message forwarded to ${recipientConnections.length} active connections for ${recipientUser.username}`);
          } else {
            console.log(`Recipient ${recipientUser.username} is offline, message stored for later delivery`);
          }
        } catch (error) {
          console.error('Error processing direct message:', error);
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
        
        // Forward message to all recipient's devices if online
        if (clients.has(data.recipientId)) {
          const recipientConnections = clients.get(data.recipientId);
          recipientConnections.forEach(connection => {
            if (connection.ws.readyState === 1) { // WebSocket.OPEN
              connection.ws.send(JSON.stringify({
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
          });
        }
        break;

      // WebRTC Signaling
      case 'call_request':
        // Check if recipient is already in a call
        if (isUserInCall(data.recipientId)) {
          const callerConnections = clients.get(userId);
          callerConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'call_busy',
                data: {
                  recipientId: data.recipientId
                }
              }));
            }
          });
          break;
        }

        // Handle call request
        if (clients.has(data.recipientId)) {
          const recipientConnections = clients.get(data.recipientId);
          recipientConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'incoming_call',
                data: {
                  callerId: userId,
                  isVideoCall: data.isVideoCall
                }
              }));
            }
          });

          // Set up call timeout
          const callId = uuidv4();
          const timeout = setTimeout(() => {
            if (activeCalls.has(callId)) {
              // Call timed out
              const call = activeCalls.get(callId);
              callStats.missedCalls++;
              
              // Notify caller
              if (clients.has(call.callerId)) {
                const callerConnections = clients.get(call.callerId);
                callerConnections.forEach(connection => {
                  if (connection.ws.readyState === 1) {
                    connection.ws.send(JSON.stringify({
                      type: 'call_timeout',
                      data: {
                        recipientId: call.recipientId
                      }
                    }));
                  }
                });
              }

              // Notify recipient
              if (clients.has(call.recipientId)) {
                const recipientConnections = clients.get(call.recipientId);
                recipientConnections.forEach(connection => {
                  if (connection.ws.readyState === 1) {
                    connection.ws.send(JSON.stringify({
                      type: 'call_timeout',
                      data: {
                        callerId: call.callerId
                      }
                    }));
                  }
                });
              }

              // Record call in database
              db.Call.create({
                id: callId,
                callerId: call.callerId,
                recipientId: call.recipientId,
                status: 'missed',
                startTime: call.startTime,
                endTime: new Date(),
                duration: 0,
                isVideoCall: call.isVideoCall
              });

              activeCalls.delete(callId);
            }
          }, CALL_TIMEOUT);

          // Store call information
          activeCalls.set(callId, {
            callerId: userId,
            recipientId: data.recipientId,
            isVideoCall: data.isVideoCall,
            startTime: new Date(),
            timeout: timeout
          });
        }
        break;

      case 'call_response':
        // Handle call response (accept/reject)
        if (clients.has(data.callerId)) {
          const callerConnections = clients.get(data.callerId);
          callerConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'call_answered',
                data: {
                  accepted: data.accepted,
                  recipientId: userId
                }
              }));
            }
          });

          // Find and update the call
          for (const [callId, call] of activeCalls.entries()) {
            if (call.callerId === data.callerId && call.recipientId === userId) {
              clearTimeout(call.timeout);
              
              if (data.accepted) {
                callStats.totalCalls++;
                callStats.successfulCalls++;
                call.status = 'active';
              } else {
                callStats.totalCalls++;
                callStats.missedCalls++;
                call.status = 'rejected';
                call.endTime = new Date();
                call.duration = call.endTime - call.startTime;
                
                // Record rejected call in database
                db.Call.create({
                  id: callId,
                  callerId: call.callerId,
                  recipientId: call.recipientId,
                  status: 'rejected',
                  startTime: call.startTime,
                  endTime: call.endTime,
                  duration: call.duration,
                  isVideoCall: call.isVideoCall
                });

                activeCalls.delete(callId);
              }
              break;
            }
          }
        }
        break;

      case 'offer':
        // Forward WebRTC offer to recipient
        if (clients.has(data.to)) {
          const recipientConnections = clients.get(data.to);
          recipientConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'offer',
                data: {
                  sdp: data.sdp,
                  from: userId
                }
              }));
            }
          });
        }
        break;

      case 'answer':
        // Forward WebRTC answer to caller
        if (clients.has(data.to)) {
          const callerConnections = clients.get(data.to);
          callerConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'answer',
                data: {
                  sdp: data.sdp,
                  from: userId
                }
              }));
            }
          });
        }
        break;

      case 'ice_candidate':
        // Forward ICE candidate to peer
        if (clients.has(data.to)) {
          const peerConnections = clients.get(data.to);
          peerConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'ice_candidate',
                data: {
                  candidate: data.candidate,
                  sdpMid: data.sdpMid,
                  sdpMLineIndex: data.sdpMLineIndex,
                  from: userId
                }
              }));
            }
          });
        }
        break;

      case 'end_call':
        // Notify peer about call end
        if (clients.has(data.to)) {
          const peerConnections = clients.get(data.to);
          peerConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'call_ended',
                data: {
                  from: userId
                }
              }));
            }
          });
        }

        // Find and update the call
        for (const [callId, call] of activeCalls.entries()) {
          if ((call.callerId === userId && call.recipientId === data.to) ||
              (call.recipientId === userId && call.callerId === data.to)) {
            clearTimeout(call.timeout);
            call.endTime = new Date();
            call.duration = call.endTime - call.startTime;
            call.status = 'completed';

            // Update call statistics
            callStats.totalDuration += call.duration;
            callStats.averageDuration = callStats.totalDuration / callStats.successfulCalls;

            // Record completed call in database
            db.Call.create({
              id: callId,
              callerId: call.callerId,
              recipientId: call.recipientId,
              status: 'completed',
              startTime: call.startTime,
              endTime: call.endTime,
              duration: call.duration,
              isVideoCall: call.isVideoCall
            });

            activeCalls.delete(callId);
            break;
          }
        }
        break;
        
      case 'READ_RECEIPT':
        // Update message as read
        await db.Message.update(
          { read: true, readAt: new Date() },
          { where: { id: data.messageId, recipientId: userId }}
        );
        
        // Notify all of sender's devices if online
        const readMessage = await db.Message.findByPk(data.messageId);
        if (readMessage && clients.has(readMessage.senderId)) {
          const senderConnections = clients.get(readMessage.senderId);
          senderConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'READ_RECEIPT',
                data: {
                  messageId: data.messageId,
                  readAt: new Date()
                }
              }));
            }
          });
        }
        break;
        
      case 'TYPING':
        // Notify all recipient's devices that user is typing
        if (clients.has(data.recipientId)) {
          const recipientConnections = clients.get(data.recipientId);
          recipientConnections.forEach(connection => {
            if (connection.ws.readyState === 1) {
              connection.ws.send(JSON.stringify({
                type: 'TYPING',
                data: {
                  userId: userId,
                  isTyping: data.isTyping
                }
              }));
            }
          });
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
    } else if (userId === 'TOKEN_EXPIRED' || userId === 'INVALID_TOKEN' || userId === 'INVALID_TOKEN_TYPE') {
      // Send token refresh request instead of closing connection immediately
      console.log('Sending token refresh request to client');
      
      const errorMessage = userId === 'TOKEN_EXPIRED' 
        ? 'Your authentication token has expired.'
        : userId === 'INVALID_TOKEN_TYPE'
        ? 'Invalid token type provided. Please use an access token.'
        : 'Your authentication token is invalid.';
        
      ws.send(JSON.stringify({
        type: 'TOKEN_REFRESH_REQUIRED',
        data: {
          message: `${errorMessage} Please refresh your token and reconnect.`,
          code: userId // TOKEN_EXPIRED, INVALID_TOKEN, or INVALID_TOKEN_TYPE
        }
      }));
      
      // Add a short timeout before closing to allow client to receive the message
      setTimeout(() => {
        const closeCode = userId === 'TOKEN_EXPIRED' ? 4003 : userId === 'INVALID_TOKEN_TYPE' ? 4004 : 4002;
        const reason = userId === 'TOKEN_EXPIRED' ? 'Token expired' : 
                      userId === 'INVALID_TOKEN_TYPE' ? 'Invalid token type' : 'Invalid token';
        ws.close(closeCode, reason);
      }, 1000);
      
      return;
    }
    
    setupAuthenticatedConnection(ws, userId, connectionId);
  });
  
  // Heartbeat to keep connections alive
  const interval = setInterval(() => {
    wss.clients.forEach((ws) => {
      if (ws.isAlive === false) {
        console.log(`Terminating inactive connection: ${ws.connectionId}`);
        return ws.terminate();
      }
      
      ws.isAlive = false;
      ws.ping();
    });
  }, 30000);
  
  wss.on('close', () => {
    clearInterval(interval);
    console.log('WebSocket server closed');
  });
};

const setupAuthenticatedConnection = (ws, userId, connectionId) => {
  console.log(`WebSocket authenticated for user: ${userId}`);
  
  // Store client connection - add to array of connections for this user
  if (!clients.has(userId)) {
    clients.set(userId, []);
  }
  
  // Add this connection to the user's connections array
  clients.get(userId).push({
    ws,
    connectionId,
    connectedAt: new Date()
  });
  
  // Add connection info to the WebSocket object
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
    handleMessage(userId, message, connectionId);
  });
  
  // Handle disconnection
  ws.on('close', () => {
    console.log('WebSocket client disconnected:', connectionId);
    
    // Remove this connection from the user's connections
    if (clients.has(userId)) {
      const connections = clients.get(userId);
      const connectionIndex = connections.findIndex(conn => conn.connectionId === connectionId);
      
      if (connectionIndex !== -1) {
        connections.splice(connectionIndex, 1);
        
        // If this was the last connection for this user, remove the user from the clients map
        // and update their status to offline
        if (connections.length === 0) {
          clients.delete(userId);
          
          // Update user as offline
          db.User.update({ isActive: false, lastActiveAt: new Date() }, { where: { id: userId } })
            .catch(err => console.error('Failed to update user inactive status:', err));
          
          // Notify user's contacts that they're offline
          notifyUserStatus(userId, false);
        } else {
          console.log(`User ${userId} still has ${connections.length} active connections`);
        }
      }
    }
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
    
    // Get the user's lastActiveAt timestamp first
    const user = await db.User.findByPk(userId, {
      attributes: ['lastActiveAt']
    });
    
    if (!user) {
      console.log(`User ${userId} not found when notifying status`);
      return;
    }
    
    // Find user's contacts
    const contacts = await db.Contact.findAll({
      where: { userId }
    });
    
    // Notify each online contact
    contacts.forEach(contact => {
      if (clients.has(contact.contactId)) {
        const contactConnections = clients.get(contact.contactId);
        contactConnections.forEach(connection => {
          if (connection.ws.readyState === 1) {
            connection.ws.send(JSON.stringify({
              type: 'USER_STATUS',
              data: {
                userId,
                isOnline,
                lastActiveAt: user.lastActiveAt,
                timestamp: new Date()
              }
            }));
          }
        });
      }
    });
  } catch (error) {
    console.error('Error notifying user status:', error);
  }
};

// Helper function to check if a user is in a call
const isUserInCall = (userId) => {
  for (const call of activeCalls.values()) {
    if ((call.callerId === userId || call.recipientId === userId) && call.status === 'active') {
      return true;
    }
  }
  return false;
};

module.exports = {
  setupWebSocketServer,
  clients
}; 