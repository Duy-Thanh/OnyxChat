/**
 * Middleware module index
 * 
 * Re-exports all middleware modules for easier imports
 */

// Import middleware from the singular 'middleware' directory
const authMiddleware = require('../middleware/auth.middleware');

// Export as expected by routes
module.exports = {
  authJwt: {
    verifyToken: authMiddleware.authenticate
  }
}; 