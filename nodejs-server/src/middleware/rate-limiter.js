/**
 * Rate Limiter Middleware
 * 
 * This middleware implements API rate limiting to protect against abuse and DoS attacks.
 * It uses an in-memory store for development and Redis for production environments.
 */

const rateLimit = require('express-rate-limit');
const RedisStore = require('rate-limit-redis');
const Redis = require('ioredis');
const config = require('../config');
const logger = require('../utils/logger');

// Default rate limit settings
const DEFAULT_WINDOW_MS = 15 * 60 * 1000; // 15 minutes
const DEFAULT_MAX_REQUESTS = 100; // limit each IP to 100 requests per windowMs

// Get configuration from environment or use defaults
const windowMs = parseInt(process.env.RATE_LIMIT_WINDOW_MS) || DEFAULT_WINDOW_MS;
const maxRequests = parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || DEFAULT_MAX_REQUESTS;

let store;

// Use Redis as store in production
if (process.env.NODE_ENV === 'production' && process.env.REDIS_URL) {
  try {
    const client = new Redis(process.env.REDIS_URL);
    
    client.on('error', (err) => {
      logger.error(`Redis error: ${err.message}`);
      // Fall back to memory store on Redis failure
      store = undefined;
    });
    
    store = new RedisStore({
      // @ts-ignore (type definition issue with newer versions)
      sendCommand: (...args) => client.call(...args),
      prefix: 'ratelimit:',
    });
    
    logger.info('Rate limiter using Redis store');
  } catch (error) {
    logger.error(`Failed to initialize Redis for rate limiting: ${error.message}`);
    logger.warn('Falling back to memory store for rate limiting');
  }
}

/**
 * Creates a rate limiter middleware with the specified options
 * 
 * @param {Object} options - Options to override defaults
 * @param {number} [options.windowMs] - Time window in milliseconds
 * @param {number} [options.max] - Maximum requests per IP in the time window
 * @param {string} [options.message] - Custom message for rate limit exceeded
 * @param {boolean} [options.skipSuccessfulRequests] - Whether to skip successful requests in count 
 * @returns {Function} Express middleware function
 */
const createRateLimiter = (options = {}) => {
  return rateLimit({
    windowMs: options.windowMs || windowMs,
    max: options.max || maxRequests,
    standardHeaders: true,
    legacyHeaders: false,
    store: store,
    skipSuccessfulRequests: options.skipSuccessfulRequests || false,
    message: options.message || {
      status: 'error',
      message: 'Too many requests, please try again later.',
    },
    keyGenerator: (req) => {
      // Use X-Forwarded-For header if behind a proxy, otherwise use IP
      return req.headers['x-forwarded-for'] || req.ip;
    },
    skip: (req) => {
      // Skip rate limiting for health checks
      return req.path === '/api/health';
    },
    handler: (req, res, next, options) => {
      logger.warn(`Rate limit exceeded for IP: ${req.ip}`);
      res.status(429).json(options.message);
    },
  });
};

// Standard rate limiter for general API access
const standardLimiter = createRateLimiter();

// Stricter rate limiter for authentication endpoints
const authLimiter = createRateLimiter({
  windowMs: 60 * 1000, // 1 minute
  max: 5, // 5 requests per minute
  message: {
    status: 'error',
    message: 'Too many authentication attempts, please try again later.',
  },
});

// Very strict rate limiter for sensitive operations
const strictLimiter = createRateLimiter({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 3, // 3 requests per 15 minutes
  message: {
    status: 'error',
    message: 'Too many sensitive operations attempted, please try again later.',
  },
});

module.exports = {
  standardLimiter,
  authLimiter,
  strictLimiter,
  createRateLimiter,
}; 