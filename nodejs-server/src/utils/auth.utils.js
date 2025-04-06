const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');

/**
 * Generate a JWT access token
 * @param {string} userId - User ID to include in the token
 * @returns {string} JWT token
 */
const generateAccessToken = (userId) => {
  try {
    // Use a shorter expiration time for access tokens - 1 hour default
    const expiresIn = process.env.JWT_EXPIRES_IN || '1h';
    
    console.log(`Generating access token for user ${userId} with expiration: ${expiresIn}`);
    
    return jwt.sign(
      { 
        sub: userId,
        type: 'access',
        iat: Math.floor(Date.now() / 1000)
      },
      process.env.JWT_SECRET,
      { expiresIn: expiresIn }
    );
  } catch (error) {
    console.error(`Error generating access token: ${error.message}`);
    throw error;
  }
};

/**
 * Generate a JWT refresh token
 * @param {string} userId - User ID to include in the token
 * @returns {string} JWT token
 */
const generateRefreshToken = (userId) => {
  try {
    // Use a longer expiration time for refresh tokens - 30 days default
    const expiresIn = process.env.JWT_REFRESH_EXPIRES_IN || '30d';
    const tokenId = uuidv4();
    
    console.log(`Generating refresh token for user ${userId} with ID ${tokenId} and expiration: ${expiresIn}`);
    
    return jwt.sign(
      { 
        sub: userId, 
        jti: tokenId,
        type: 'refresh',
        iat: Math.floor(Date.now() / 1000)
      },
      process.env.JWT_REFRESH_SECRET,
      { expiresIn: expiresIn }
    );
  } catch (error) {
    console.error(`Error generating refresh token: ${error.message}`);
    throw error;
  }
};

/**
 * Verify JWT token
 * @param {string} token - JWT token to verify
 * @param {boolean} isRefreshToken - Whether this is a refresh token
 * @returns {Object} Decoded token payload
 * @throws {Error} If token is invalid or expired
 */
const verifyToken = (token, isRefreshToken = false) => {
  try {
    const secret = isRefreshToken ? process.env.JWT_REFRESH_SECRET : process.env.JWT_SECRET;
    const decoded = jwt.verify(token, secret);
    
    // Additional validation: check token type if present
    if (decoded.type) {
      const expectedType = isRefreshToken ? 'refresh' : 'access';
      if (decoded.type !== expectedType) {
        throw new Error(`Invalid token type: expected ${expectedType}, got ${decoded.type}`);
      }
    }
    
    return decoded;
  } catch (error) {
    if (error instanceof jwt.TokenExpiredError) {
      console.warn(`Token expired at ${new Date(error.expiredAt)}`);
      throw new Error('Token expired');
    } else if (error instanceof jwt.JsonWebTokenError) {
      console.warn(`Invalid token: ${error.message}`);
      throw new Error(`Invalid token: ${error.message}`);
    }
    console.error(`Error verifying token: ${error.message}`);
    throw error;
  }
};

/**
 * Extract access token from Authorization header
 * @param {string} authHeader - Authorization header string
 * @returns {string|null} Extracted token or null if not found
 */
const extractTokenFromHeader = (authHeader) => {
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null;
  }
  return authHeader.substring(7);
};

/**
 * Hash a password
 * @param {string} password - Plain text password
 * @returns {Promise<string>} Hashed password
 */
const hashPassword = async (password) => {
  const saltRounds = 10;
  return bcrypt.hash(password, saltRounds);
};

/**
 * Compare password with hash
 * @param {string} password - Plain text password
 * @param {string} hash - Hashed password
 * @returns {Promise<boolean>} True if match, false otherwise
 */
const comparePassword = async (password, hash) => {
  return bcrypt.compare(password, hash);
};

module.exports = {
  generateAccessToken,
  generateRefreshToken,
  verifyToken,
  extractTokenFromHeader,
  hashPassword,
  comparePassword
}; 