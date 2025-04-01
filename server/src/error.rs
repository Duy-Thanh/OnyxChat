use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;
use std::{error::Error as StdError, fmt};

use crate::auth::AuthError;

#[derive(Debug)]
pub enum AppError {
    Auth(AuthError),
    NotFound(String),
    BadRequest(String),
    Forbidden(String),
    Internal(String),
    Conflict(String),
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Auth(err) => write!(f, "Authentication error: {}", err),
            Self::NotFound(msg) => write!(f, "Not found: {}", msg),
            Self::BadRequest(msg) => write!(f, "Bad request: {}", msg),
            Self::Forbidden(msg) => write!(f, "Forbidden: {}", msg),
            Self::Internal(msg) => write!(f, "Internal server error: {}", msg),
            Self::Conflict(msg) => write!(f, "Conflict: {}", msg),
        }
    }
}

impl StdError for AppError {}

impl From<AuthError> for AppError {
    fn from(err: AuthError) -> Self {
        Self::Auth(err)
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error_message) = match self {
            Self::Auth(AuthError::InvalidToken) => (StatusCode::UNAUTHORIZED, "Invalid token".to_string()),
            Self::Auth(AuthError::MissingToken) => (StatusCode::UNAUTHORIZED, "Missing token".to_string()),
            Self::Auth(AuthError::TokenCreation) => (
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to create token".to_string(),
            ),
            Self::Auth(AuthError::PasswordHashing) => (
                StatusCode::INTERNAL_SERVER_ERROR,
                "Failed to hash password".to_string(),
            ),
            Self::Auth(AuthError::InvalidPassword) => (
                StatusCode::UNAUTHORIZED,
                "Invalid username or password".to_string(),
            ),
            Self::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
            Self::BadRequest(msg) => (StatusCode::BAD_REQUEST, msg),
            Self::Forbidden(msg) => (StatusCode::FORBIDDEN, msg),
            Self::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
            Self::Conflict(msg) => (StatusCode::CONFLICT, msg),
        };

        let body = Json(json!({
            "error": error_message,
        }));

        (status, body).into_response()
    }
}

// Convenience type for handling results with AppError
pub type Result<T> = std::result::Result<T, AppError>; 