const { verifyToken, extractTokenFromHeader } = require('../utils/auth.utils');
const { AuthenticationError, TokenExpiredError } = require('../utils/error.utils');
const db = require('../models');

/**
 * Middleware to authenticate requests
 * @param {Object} req - Express request object
 * @param {Object} res - Express response object
 * @param {Function} next - Express next function
 */
const authenticate = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    const token = extractTokenFromHeader(authHeader);
    
    if (!token) {
      console.log('No token provided in authorization header');
      return next(new AuthenticationError('No token provided'));
    }
    
    try {
      console.log(`Authenticating request to ${req.originalUrl} with token: ${token.substring(0, 10)}...`);
      const decoded = verifyToken(token);
      
      // Validate token type if available (should be 'access')
      if (decoded.type && decoded.type !== 'access') {
        console.warn(`Invalid token type: ${decoded.type}, expected 'access'`);
        return next(new AuthenticationError('Invalid token type. Please use an access token.'));
      }
      
      // Get user from database
      const user = await db.User.findByPk(decoded.sub);
      if (!user) {
        console.error(`User not found for ID: ${decoded.sub}`);
        return next(new AuthenticationError('User not found'));
      }
      
      // Update user's last active timestamp
      await user.update({ lastActiveAt: new Date() });
      
      console.log(`Request authenticated for user: ${user.username} (${user.id})`);
      
      // Attach user to request
      req.user = user;
      next();
    } catch (error) {
      if (error.message === 'Token expired') {
        console.log('Token expired error detected, returning TokenExpiredError');
        return next(new TokenExpiredError());
      }
      console.warn(`Authentication error: ${error.message}`);
      return next(new AuthenticationError(error.message));
    }
  } catch (error) {
    console.error(`Unexpected error in authentication middleware: ${error.message}`, error);
    next(error);
  }
};

/**
 * Middleware to optionally authenticate requests
 * @param {Object} req - Express request object
 * @param {Object} res - Express response object
 * @param {Function} next - Express next function
 */
const optionalAuthenticate = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader) {
      return next();
    }
    
    const token = extractTokenFromHeader(authHeader);
    if (!token) {
      return next();
    }
    
    try {
      const decoded = verifyToken(token);
      
      // Get user from database
      const user = await db.User.findByPk(decoded.sub);
      if (user) {
        req.user = user;
      }
      next();
    } catch (error) {
      // Continue without authentication
      next();
    }
  } catch (error) {
    next(error);
  }
};

module.exports = {
  authenticate,
  optionalAuthenticate
}; 