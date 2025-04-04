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
      return next(new AuthenticationError('No token provided'));
    }
    
    try {
      const decoded = verifyToken(token);
      
      // Get user from database
      const user = await db.User.findByPk(decoded.sub);
      if (!user) {
        return next(new AuthenticationError('User not found'));
      }
      
      // Attach user to request
      req.user = user;
      next();
    } catch (error) {
      if (error.message === 'Token expired') {
        return next(new TokenExpiredError());
      }
      return next(new AuthenticationError(error.message));
    }
  } catch (error) {
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