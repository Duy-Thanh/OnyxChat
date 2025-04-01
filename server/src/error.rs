use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use std::{error::Error as StdError, fmt};

use crate::auth::AuthError;

// Error type for the application
#[derive(Debug)]
pub enum AppError {
    NotFound(String),
    BadRequest(String),
    Unauthorized(String),
    Forbidden(String),
    Conflict(String),
    Internal(String),
    Auth(AuthError),
}

// Custom result type
pub type Result<T, E = AppError> = std::result::Result<T, E>;

// Auth specific errors
#[derive(Debug)]
pub enum AuthError {
    InvalidToken,
    ExpiredToken,
    InvalidPassword,
    UserNotFound,
}

// JSON error response
#[derive(Serialize, Deserialize)]
pub struct ErrorResponse {
    pub error: String,
}

// Implementation for displaying errors
impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::NotFound(msg) => write!(f, "Not found: {}", msg),
            Self::BadRequest(msg) => write!(f, "Bad request: {}", msg),
            Self::Unauthorized(msg) => write!(f, "Unauthorized: {}", msg),
            Self::Forbidden(msg) => write!(f, "Forbidden: {}", msg),
            Self::Conflict(msg) => write!(f, "Conflict: {}", msg),
            Self::Internal(msg) => write!(f, "Internal server error: {}", msg),
            Self::Auth(e) => write!(f, "Authentication error: {}", e),
        }
    }
}

// Implementation for displaying auth errors
impl fmt::Display for AuthError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidToken => write!(f, "Invalid token"),
            Self::ExpiredToken => write!(f, "Token expired"),
            Self::InvalidPassword => write!(f, "Invalid password"),
            Self::UserNotFound => write!(f, "User not found"),
        }
    }
}

// Implementation for converting errors to HTTP responses
impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error_message) = match self {
            Self::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
            Self::BadRequest(msg) => (StatusCode::BAD_REQUEST, msg),
            Self::Unauthorized(msg) => (StatusCode::UNAUTHORIZED, msg),
            Self::Forbidden(msg) => (StatusCode::FORBIDDEN, msg),
            Self::Conflict(msg) => (StatusCode::CONFLICT, msg),
            Self::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
            Self::Auth(AuthError::InvalidToken) => (StatusCode::UNAUTHORIZED, "Invalid token".to_string()),
            Self::Auth(AuthError::ExpiredToken) => (StatusCode::UNAUTHORIZED, "Token expired".to_string()),
            Self::Auth(AuthError::InvalidPassword) => (StatusCode::UNAUTHORIZED, "Invalid username or password".to_string()),
            Self::Auth(AuthError::UserNotFound) => (StatusCode::UNAUTHORIZED, "Invalid username or password".to_string()),
        };

        (status, Json(ErrorResponse { error: error_message })).into_response()
    }
}

// Helper function for AppError
impl AppError {
    pub fn not_found(msg: impl Into<String>) -> Self {
        Self::NotFound(msg.into())
    }

    pub fn bad_request(msg: impl Into<String>) -> Self {
        Self::BadRequest(msg.into())
    }

    pub fn unauthorized(msg: impl Into<String>) -> Self {
        Self::Unauthorized(msg.into())
    }

    pub fn forbidden(msg: impl Into<String>) -> Self {
        Self::Forbidden(msg.into())
    }

    pub fn conflict(msg: impl Into<String>) -> Self {
        Self::Conflict(msg.into())
    }

    pub fn internal(msg: impl Into<String>) -> Self {
        Self::Internal(msg.into())
    }
}

impl StdError for AppError {}

impl From<AuthError> for AppError {
    fn from(err: AuthError) -> Self {
        Self::Auth(err)
    }
} 