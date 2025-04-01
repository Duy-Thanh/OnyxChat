use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use std::{fmt, result};

// Error type for the application
#[derive(Debug)]
pub enum AppError {
    NotFound(String),
    BadRequest(String),
    Unauthorized(String),
    Forbidden(String),
    Conflict(String),
    Internal(String),
    Auth(crate::middleware::auth::AuthError),
}

// Custom result type
pub type Result<T, E = AppError> = result::Result<T, E>;

// JSON error response
#[derive(Debug, Serialize, Deserialize)]
pub struct ErrorResponse {
    pub status: String,
    pub message: String,
}

// Implementation for displaying errors
impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let message = match self {
            AppError::NotFound(msg) => msg,
            AppError::BadRequest(msg) => msg,
            AppError::Unauthorized(msg) => msg,
            AppError::Forbidden(msg) => msg,
            AppError::Conflict(msg) => msg,
            AppError::Internal(msg) => msg,
            AppError::Auth(err) => return write!(f, "Authentication error: {}", err),
        };
        write!(f, "{}", message)
    }
}

// Implementation for converting errors to HTTP responses
impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error_message) = match &self {
            AppError::NotFound(msg) => (StatusCode::NOT_FOUND, msg.clone()),
            AppError::BadRequest(msg) => (StatusCode::BAD_REQUEST, msg.clone()),
            AppError::Unauthorized(msg) => (StatusCode::UNAUTHORIZED, msg.clone()),
            AppError::Forbidden(msg) => (StatusCode::FORBIDDEN, msg.clone()),
            AppError::Conflict(msg) => (StatusCode::CONFLICT, msg.clone()),
            AppError::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg.clone()),
            AppError::Auth(crate::middleware::auth::AuthError::InvalidToken) => {
                (StatusCode::UNAUTHORIZED, "Invalid token".to_string())
            }
            AppError::Auth(crate::middleware::auth::AuthError::ExpiredToken) => {
                (StatusCode::UNAUTHORIZED, "Token has expired".to_string())
            }
            AppError::Auth(crate::middleware::auth::AuthError::InvalidPassword) => {
                (StatusCode::UNAUTHORIZED, "Invalid password".to_string())
            }
        };

        let body = Json(ErrorResponse {
            status: status.to_string(),
            message: error_message,
        });

        (status, body).into_response()
    }
}

// Helper function for AppError
impl AppError {
    pub fn not_found(message: impl Into<String>) -> Self {
        AppError::NotFound(message.into())
    }

    pub fn bad_request(message: impl Into<String>) -> Self {
        AppError::BadRequest(message.into())
    }

    pub fn unauthorized(message: impl Into<String>) -> Self {
        AppError::Unauthorized(message.into())
    }

    pub fn forbidden(message: impl Into<String>) -> Self {
        AppError::Forbidden(message.into())
    }

    pub fn conflict(message: impl Into<String>) -> Self {
        AppError::Conflict(message.into())
    }

    pub fn internal(message: impl Into<String>) -> Self {
        AppError::Internal(message.into())
    }
    
    pub fn auth(message: impl Into<String>) -> Self {
        AppError::Unauthorized(message.into())
    }
    
    pub fn not_implemented(message: impl Into<String>) -> Self {
        AppError::Internal(message.into())
    }
}

impl std::error::Error for AppError {}

// Implement conversions from other error types
impl From<sqlx::Error> for AppError {
    fn from(err: sqlx::Error) -> Self {
        match err {
            sqlx::Error::RowNotFound => AppError::not_found("Resource not found"),
            _ => AppError::internal(format!("Database error: {}", err)),
        }
    }
}

impl From<serde_json::Error> for AppError {
    fn from(err: serde_json::Error) -> Self {
        AppError::bad_request(format!("JSON error: {}", err))
    }
}

impl From<jsonwebtoken::errors::Error> for AppError {
    fn from(err: jsonwebtoken::errors::Error) -> Self {
        match err.kind() {
            jsonwebtoken::errors::ErrorKind::ExpiredSignature => {
                AppError::Auth(crate::middleware::auth::AuthError::ExpiredToken)
            }
            jsonwebtoken::errors::ErrorKind::InvalidToken => AppError::Auth(crate::middleware::auth::AuthError::InvalidToken),
            _ => AppError::unauthorized(format!("JWT error: {}", err)),
        }
    }
}

impl From<argon2::password_hash::Error> for AppError {
    fn from(err: argon2::password_hash::Error) -> Self {
        AppError::internal(format!("Password hash error: {}", err))
    }
}

impl From<std::io::Error> for AppError {
    fn from(err: std::io::Error) -> Self {
        AppError::internal(format!("IO error: {}", err))
    }
}

impl From<validator::ValidationErrors> for AppError {
    fn from(errors: validator::ValidationErrors) -> Self {
        AppError::bad_request(format!("Validation error: {}", errors))
    }
}

impl From<uuid::Error> for AppError {
    fn from(err: uuid::Error) -> Self {
        AppError::bad_request(format!("Invalid UUID: {}", err))
    }
} 