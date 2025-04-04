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

// Create HTTP server
const server = http.createServer(app);

// Set up WebSocket server
const wss = new WebSocket.Server({ server });
setupWebSocketServer(wss);

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
const isMockMode = process.env.NODE_ENV === 'development' || process.env.USE_MOCK_DB === 'true';

// Handle startup based on mode
if (isMockMode) {
  console.log('Starting server in development mode with mock database');
  startServer();
} else {
  // In production, try to sync the database before starting
  db.sequelize.sync({ alter: true })
    .then(() => {
      console.log('Database connected and synced');
      startServer();
    })
    .catch(err => {
      console.error('Failed to connect to database:', err);
      console.log('Starting server without database connection');
      startServer();
    });
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