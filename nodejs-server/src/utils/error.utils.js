/**
 * Custom application error class
 */
class AppError extends Error {
  constructor(message, statusCode, errorCode = null) {
    super(message);
    this.statusCode = statusCode;
    this.errorCode = errorCode;
    this.isOperational = true;
    
    Error.captureStackTrace(this, this.constructor);
  }
}

/**
 * Error for authentication failures
 */
class AuthenticationError extends AppError {
  constructor(message = 'Authentication failed', errorCode = 'AUTH_FAILED') {
    super(message, 401, errorCode);
  }
}

/**
 * Error for expired tokens
 */
class TokenExpiredError extends AuthenticationError {
  constructor(message = 'Token expired') {
    super(message, 'TOKEN_EXPIRED');
  }
}

/**
 * Error for access denied scenarios
 */
class ForbiddenError extends AppError {
  constructor(message = 'Access denied', errorCode = 'ACCESS_DENIED') {
    super(message, 403, errorCode);
  }
}

/**
 * Error for resource not found
 */
class NotFoundError extends AppError {
  constructor(resource = 'Resource', errorCode = 'NOT_FOUND') {
    super(`${resource} not found`, 404, errorCode);
  }
}

/**
 * Error for validation failures
 */
class ValidationError extends AppError {
  constructor(message = 'Validation failed', errors = null, errorCode = 'VALIDATION_FAILED') {
    super(message, 400, errorCode);
    this.errors = errors;
  }
}

/**
 * Error for resource already exists
 */
class ConflictError extends AppError {
  constructor(resource = 'Resource', errorCode = 'CONFLICT') {
    super(`${resource} already exists`, 409, errorCode);
  }
}

/**
 * Error for database failures
 */
class DatabaseError extends AppError {
  constructor(message = 'Database operation failed', errorCode = 'DB_ERROR') {
    super(message, 500, errorCode);
  }
}

/**
 * Error for server-side failures
 */
class ServerError extends AppError {
  constructor(message = 'Internal server error', errorCode = 'SERVER_ERROR') {
    super(message, 500, errorCode);
  }
}

module.exports = {
  AppError,
  AuthenticationError,
  TokenExpiredError,
  ForbiddenError,
  NotFoundError,
  ValidationError,
  ConflictError,
  DatabaseError,
  ServerError
}; 