require('dotenv').config();
const express = require('express');
const cors = require('cors');
const morgan = require('morgan');
const http = require('http');
const WebSocket = require('ws');
const { setupWebSocketServer } = require('./websocket');
const db = require('./models');

// Import routes
const authRoutes = require('./routes/auth.routes');
const userRoutes = require('./routes/user.routes');
const messageRoutes = require('./routes/message.routes');
const cryptoRoutes = require('./routes/crypto.routes');
const contactsRoutes = require('./routes/contacts.routes');

// Initialize express app
const app = express();
const PORT = process.env.PORT || 8081;

// Middleware
app.use(cors());
app.use(morgan('dev'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Health check route
app.get('/api/health', (req, res) => {
  res.status(200).send('OK');
});

// Basic test route
app.get('/api/hello', (req, res) => {
  res.json({ message: 'Hello, World from OnyxChat Server!', status: 'success' });
});

// API routes
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/messages', messageRoutes);
app.use('/api/crypto', cryptoRoutes);
app.use('/api/contacts', contactsRoutes);

// Add a route to handle WebSocket connections for better debugging
app.get('/ws', (req, res) => {
  console.log('HTTP GET request to WebSocket endpoint received');
  console.log('Query parameters:', req.query);
  console.log('Headers:', req.headers);
  
  // For non-WebSocket requests to the WebSocket endpoint
  res.status(200).send('WebSocket endpoint is available. Please use a WebSocket client to connect.');
});

// Create HTTP server
const server = http.createServer(app);

// Set up WebSocket server
const wss = new WebSocket.Server({ 
  server,
  path: '/ws', // Explicitly set the path
  verifyClient: (info, cb) => {
    console.log('Verifying WebSocket connection...');
    console.log('Request URL:', info.req.url);
    console.log('Request headers:', info.req.headers);
    
    // Allow all connections at this stage
    // Authentication will be handled in the connection handler
    cb(true);
  }
});

// Log WebSocket server errors
wss.on('error', (error) => {
  console.error('WebSocket server error:', error);
});

// Setup the WebSocket server with our handlers
setupWebSocketServer(wss);

// Create test user in mock mode
if (process.env.USE_MOCK_DB === 'true' || process.env.NODE_ENV === 'test') {
  console.log('Creating test user for mock mode');
  const bcrypt = require('bcryptjs');
  const { v4: uuidv4 } = require('uuid');
  
  const createTestUser = async () => {
    try {
      // Check if user already exists
      const existingUser = await db.User.findOne({ where: { username: 'testuser' } });
      if (existingUser) {
        console.log('Test user already exists with ID:', existingUser.id);
        
        // Make sure the toProfile method is added
        if (!existingUser.toProfile) {
          existingUser.toProfile = function() {
            return {
              id: this.id,
              username: this.username,
              displayName: this.displayName,
              isActive: this.isActive,
              lastActiveAt: this.lastActiveAt
            };
          };
          console.log('Added toProfile method to existing user');
        }
        return;
      }
      
      // Create test user with ID
      const testUserId = uuidv4();
      console.log('Generated test user ID:', testUserId);
      
      const hashedPassword = await bcrypt.hash('password123', 10);
      console.log('Hashed password for test user');
      
      // Create with explicit toProfile method
      const user = await db.User.create({
        id: testUserId,
        username: 'testuser',
        password: hashedPassword,
        displayName: 'Test User',
        isActive: false,
        lastActiveAt: new Date(),
        toProfile: function() {
          return {
            id: this.id,
            username: this.username,
            displayName: this.displayName,
            isActive: this.isActive,
            lastActiveAt: this.lastActiveAt
          };
        }
      });
      
      console.log('Created test user with ID:', user.id);
      const profile = user.toProfile();
      console.log('Test user profile:', profile);
    } catch (error) {
      console.error('Error creating test user:', error);
    }
  };
  
  createTestUser();
}

// Error handling middleware
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(err.statusCode || 500).json({
    status: 'error',
    message: err.message || 'Internal Server Error',
  });
});

// Start server without requiring database sync
const startServer = () => {
  server.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
  });
};

// Determine if we're in mock mode
const isMockMode = process.env.NODE_ENV === 'test' || process.env.USE_MOCK_DB === 'true';

// Handle startup based on mode
if (isMockMode) {
  console.log('Starting server in mock database mode');
  startServer();
} else {
  // In production, try to sync the database before starting
  if (db.sequelize) {
    db.sequelize.sync({ alter: false })
      .then(() => {
        console.log('Database connected and synced');
        startServer();
      })
      .catch(err => {
        console.error('Failed to connect to database:', err);
        console.log('Starting server without database connection');
        startServer();
      });
  } else {
    console.error('No database connection available');
    startServer();
  }
}

// Handle graceful shutdown
process.on('SIGTERM', () => {
  console.info('SIGTERM signal received.');
  console.log('Closing HTTP server.');
  server.close(() => {
    console.log('HTTP server closed.');
    if (db.sequelize && typeof db.sequelize.close === 'function') {
      db.sequelize.close().then(() => {
        console.log('Database connection closed.');
        process.exit(0);
      });
    } else {
      process.exit(0);
    }
  });
}); 