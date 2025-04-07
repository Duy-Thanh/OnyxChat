/**
 * Configuration Module
 * 
 * Centralizes all configuration settings and environment variables.
 * Uses environment variables with sane defaults.
 */

require('dotenv').config();

const env = process.env.NODE_ENV || 'development';
const isProd = env === 'production';

// Database configuration
const database = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432'),
  database: process.env.DB_NAME || 'onyxchat',
  user: process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
  ssl: isProd ? { rejectUnauthorized: false } : false,
  connectionTimeoutMillis: 5000,
  idleTimeoutMillis: 30000,
  max: 20, // connection pool max size
};

// Server configuration
const server = {
  port: parseInt(process.env.PORT || '8081'),
  host: process.env.HOST || '0.0.0.0',
  env,
  isProd,
  corsOrigin: process.env.CORS_ORIGIN || '*',
  logLevel: process.env.LOG_LEVEL || (isProd ? 'info' : 'debug'),
};

// Security configuration
const security = {
  jwt: {
    secret: process.env.JWT_SECRET,
    refreshSecret: process.env.JWT_REFRESH_SECRET,
    expiresIn: process.env.JWT_EXPIRES_IN || '1h',
    refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '7d',
  },
  encryption: {
    key: process.env.ENCRYPTION_KEY,
  },
  // Password hashing settings
  bcrypt: {
    saltRounds: isProd ? 12 : 10, // Higher rounds in production for better security
  },
  rateLimiting: {
    enabled: isProd || process.env.ENABLE_RATE_LIMITING === 'true',
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS || (15 * 60 * 1000).toString()),
    maxRequests: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS || '100'),
  }
};

// WebSocket configuration
const websocket = {
  heartbeatInterval: parseInt(process.env.WS_HEARTBEAT_INTERVAL || '30000'),
  path: process.env.WS_PATH || '/ws',
};

// File upload configuration
const fileUpload = {
  maxSize: parseInt(process.env.MAX_FILE_SIZE || '524288000'), // 500MB in bytes (default)
  storagePath: process.env.FILE_STORAGE_PATH || './uploads',
  allowedTypes: process.env.ALLOWED_FILE_TYPES || 'image/*,video/*,audio/*,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/zip,application/x-zip-compressed',
};

// Redis configuration (if used)
const redis = process.env.REDIS_URL ? {
  url: process.env.REDIS_URL,
  enabled: true,
} : {
  enabled: false,
};

// HTTPS configuration
const https = {
  enabled: process.env.ENABLE_HTTPS === 'true',
  certPath: process.env.SSL_CERT_PATH || '/certs/server.crt',
  keyPath: process.env.SSL_KEY_PATH || '/certs/server.key',
};

// Validate critical configuration
function validateConfig() {
  const errors = [];

  if (isProd) {
    if (!security.jwt.secret || security.jwt.secret.length < 32) {
      errors.push('JWT_SECRET must be defined and at least 32 characters for production');
    }
    if (!security.jwt.refreshSecret || security.jwt.refreshSecret.length < 32) {
      errors.push('JWT_REFRESH_SECRET must be defined and at least 32 characters for production');
    }
    if (!security.encryption.key || security.encryption.key.length < 24) {
      errors.push('ENCRYPTION_KEY must be defined and at least 24 characters for production');
    }
    if (database.password === 'postgres') {
      //errors.push('Default database password is being used in production');
    }
  }

  if (errors.length > 0) {
    if (isProd) {
      throw new Error(`Configuration validation failed:\n${errors.join('\n')}`);
    } else {
      console.warn(`Configuration warnings:\n${errors.join('\n')}`);
    }
  }
}

// Only validate in production to avoid issues during development
if (isProd) {
  validateConfig();
}

module.exports = {
  database,
  server,
  security,
  websocket,
  redis,
  https,
  fileUpload,
  isProd,
}; 