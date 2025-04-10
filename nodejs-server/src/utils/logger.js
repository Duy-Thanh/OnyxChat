/**
 * Logger Module
 * 
 * Provides consistent logging functionality across the application.
 * Configures different log formats and destinations based on environment.
 */

const winston = require('winston');
const { format } = winston;
const path = require('path');
const fs = require('fs');

// Ensure logs directory exists
const logDir = path.join(process.cwd(), 'logs');
if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true });
}

// Determine environment settings
const isProd = process.env.NODE_ENV === 'production';
const logLevel = process.env.LOG_LEVEL || (isProd ? 'info' : 'debug');

// Custom format for logging request information
const requestFormat = format((info) => {
  if (info.request) {
    const req = info.request;
    info.message = `${req.method} ${req.url} - ${info.message}`;
    delete info.request;
  }
  return info;
});

// Console format for development
const consoleFormat = format.combine(
  format.colorize(),
  format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
  requestFormat(),
  format.printf(({ level, message, timestamp, ...meta }) => {
    const metaStr = Object.keys(meta).length ? 
      '\n' + JSON.stringify(meta, null, 2) : '';
    return `[${timestamp}] ${level}: ${message}${metaStr}`;
  })
);

// File format for production logs (no colors, structured JSON)
const fileFormat = format.combine(
  format.timestamp(),
  requestFormat(),
  format.json()
);

// Define transports based on environment
const transports = [];

// Always log to console in development
if (!isProd) {
  transports.push(new winston.transports.Console({
    format: consoleFormat,
    level: logLevel
  }));
} else {
  // Log errors to separate file in production
  transports.push(
    new winston.transports.File({
      filename: path.join(logDir, 'error.log'),
      level: 'error',
      format: fileFormat,
      maxsize: 10 * 1024 * 1024, // 10MB
      maxFiles: 5,
      tailable: true
    }),
    new winston.transports.File({
      filename: path.join(logDir, 'combined.log'),
      format: fileFormat,
      level: logLevel,
      maxsize: 10 * 1024 * 1024, // 10MB
      maxFiles: 5,
      tailable: true
    })
  );
  
  // Also log to console in production, but with less verbosity
  transports.push(new winston.transports.Console({
    format: consoleFormat,
    level: 'info'
  }));
}

// Create the logger
const logger = winston.createLogger({
  level: logLevel,
  levels: winston.config.npm.levels,
  transports
});

// Log any unhandled errors
process.on('uncaughtException', (error) => {
  logger.error('Uncaught Exception', { error: error.stack || error.toString() });
  if (isProd) {
    // In production, exit process on uncaught exceptions to allow process manager to restart
    process.exit(1);
  }
});

process.on('unhandledRejection', (reason) => {
  logger.error('Unhandled Rejection', { 
    reason: reason instanceof Error ? reason.stack : reason 
  });
});

/**
 * Attaches request info to log messages
 * @param {Object} req - Express request object
 * @returns {Object} Winston logger with request context
 */
logger.withRequest = (req) => {
  const requestInfo = {
    method: req.method,
    url: req.originalUrl || req.url,
    ip: req.ip || req.connection.remoteAddress,
    id: req.id // Assumes you're using an ID middleware
  };
  
  // Create a child logger with the request context
  return {
    info: (message, meta = {}) => logger.info(message, { request: requestInfo, ...meta }),
    warn: (message, meta = {}) => logger.warn(message, { request: requestInfo, ...meta }),
    error: (message, meta = {}) => logger.error(message, { request: requestInfo, ...meta }),
    debug: (message, meta = {}) => logger.debug(message, { request: requestInfo, ...meta }),
    http: (message, meta = {}) => logger.http(message, { request: requestInfo, ...meta }),
  };
};

module.exports = logger;