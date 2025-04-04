require('dotenv').config();
const express = require('express');
const cors = require('cors');
const morgan = require('morgan');
const { v4: uuidv4 } = require('uuid');

// Initialize express app
const app = express();
const PORT = process.env.PORT || 8081;

// Middleware
app.use(cors());
app.use(morgan('dev'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// In-memory data stores
const users = [];
const messages = [];
const refreshTokens = [];

// Initialize with a test user
users.push({
  id: '1',
  username: 'testuser',
  displayName: 'Test User',
  email: 'test@example.com',
  passwordHash: '$2a$10$7lmJeAK93bE0NYKy9jryQehGIZbVg3L6e4Vl53NoZgG3oMjgMVqAy', // password: test123
  isActive: true,
  lastActiveAt: new Date(),
  createdAt: new Date(),
  updatedAt: new Date()
});

// Health check route
app.get('/api/health', (req, res) => {
  res.status(200).send('OK');
});

// Basic test route
app.get('/api/hello', (req, res) => {
  res.json({ 
    message: 'Hello, World from OnyxChat Simple Server!', 
    status: 'success' 
  });
});

// User routes

// Get all users
app.get('/api/users', (req, res) => {
  res.json({
    status: 'success',
    data: {
      users: users.map(user => ({
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        isActive: user.isActive,
        lastActiveAt: user.lastActiveAt
      }))
    }
  });
});

// Register new user
app.post('/api/auth/register', (req, res) => {
  const { username, email, password, displayName } = req.body;
  
  // Simple validation
  if (!username || !email || !password) {
    return res.status(400).json({
      status: 'error',
      message: 'Missing required fields'
    });
  }
  
  // Check if username already exists
  if (users.find(u => u.username === username)) {
    return res.status(409).json({
      status: 'error',
      message: 'Username already taken'
    });
  }
  
  // Create new user
  const newUser = {
    id: uuidv4(),
    username,
    email,
    passwordHash: '$2a$10$7lmJeAK93bE0NYKy9jryQehGIZbVg3L6e4Vl53NoZgG3oMjgMVqAy', // We're not actually hashing for this demo
    displayName: displayName || username,
    isActive: true,
    lastActiveAt: new Date(),
    createdAt: new Date(),
    updatedAt: new Date()
  };
  
  users.push(newUser);
  
  // Generate "tokens"
  const accessToken = `demo-access-token-${newUser.id}`;
  const refreshToken = `demo-refresh-token-${newUser.id}`;
  
  refreshTokens.push({
    token: refreshToken,
    userId: newUser.id,
    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) // 7 days
  });
  
  res.status(201).json({
    status: 'success',
    message: 'User registered successfully',
    data: {
      user: {
        id: newUser.id,
        username: newUser.username,
        displayName: newUser.displayName,
        isActive: newUser.isActive,
        lastActiveAt: newUser.lastActiveAt
      },
      tokens: {
        accessToken,
        refreshToken
      }
    }
  });
});

// Login user
app.post('/api/auth/login', (req, res) => {
  const { username, password } = req.body;
  
  // Simple validation
  if (!username || !password) {
    return res.status(400).json({
      status: 'error',
      message: 'Username and password are required'
    });
  }
  
  // Find user
  const user = users.find(u => u.username === username);
  
  if (!user) {
    return res.status(401).json({
      status: 'error',
      message: 'Invalid credentials'
    });
  }
  
  // In a real app, we would check the password here
  // For demo, we'll just accept any password and update last active
  user.lastActiveAt = new Date();
  user.isActive = true;
  
  // Generate "tokens"
  const accessToken = `demo-access-token-${user.id}`;
  const refreshToken = `demo-refresh-token-${user.id}`;
  
  refreshTokens.push({
    token: refreshToken,
    userId: user.id,
    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) // 7 days
  });
  
  res.json({
    status: 'success',
    message: 'Login successful',
    data: {
      user: {
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        isActive: user.isActive,
        lastActiveAt: user.lastActiveAt
      },
      tokens: {
        accessToken,
        refreshToken
      }
    }
  });
});

// Get user profile
app.get('/api/auth/me', (req, res) => {
  // In a real app, we would authenticate the request
  // For demo, we'll just return the first user
  const user = users[0];
  
  res.json({
    status: 'success',
    data: {
      user: {
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        isActive: user.isActive,
        lastActiveAt: user.lastActiveAt
      }
    }
  });
});

// Send a message
app.post('/api/messages', (req, res) => {
  const { recipientId, content } = req.body;
  
  // Simple validation
  if (!recipientId || !content) {
    return res.status(400).json({
      status: 'error',
      message: 'Recipient ID and content are required'
    });
  }
  
  // In a real app, we would get the sender ID from the authenticated user
  // For demo, we'll use the first user as sender
  const senderId = users[0].id;
  
  // Create message
  const message = {
    id: uuidv4(),
    senderId,
    recipientId,
    content,
    contentType: 'text',
    encrypted: false,
    sent: true,
    received: false,
    receivedAt: null,
    read: false,
    readAt: null,
    deleted: false,
    deletedAt: null,
    createdAt: new Date(),
    updatedAt: new Date()
  };
  
  messages.push(message);
  
  res.status(201).json({
    status: 'success',
    message: 'Message sent successfully',
    data: {
      message
    }
  });
});

// Get messages
app.get('/api/messages', (req, res) => {
  // In a real app, we would get the user ID from the authenticated user
  // and filter messages accordingly
  // For demo, we'll just return all messages
  
  res.json({
    status: 'success',
    data: {
      messages
    }
  });
});

// Start server
app.listen(PORT, () => {
  console.log(`Simple server running on port ${PORT}`);
}); 