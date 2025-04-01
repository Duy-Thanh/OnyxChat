use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Error, Debug)]
pub enum AppError {
    #[error("Authentication error: {0}")]
    Auth(String),

    #[error("Authorization error: {0}")]
    Forbidden(String),

    #[error("Not found: {0}")]
    NotFound(String),

    #[error("Validation error: {0}")]
    Validation(String),

    #[error("Database error: {0}")]
    Database(#[from] sqlx::Error),

    #[error("Encryption error: {0}")]
    Encryption(String),

    #[error("Internal server error: {0}")]
    Internal(String),

    #[error("Configuration error: {0}")]
    Config(String),
    
    #[error("Not implemented: {0}")]
    NotImplemented(String),
}

#[derive(Serialize, Deserialize)]
pub struct ErrorResponse {
    pub error: String,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub details: Option<serde_json::Value>,
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error_response) = match self {
            AppError::Auth(message) => (
                StatusCode::UNAUTHORIZED,
                ErrorResponse {
                    error: "UNAUTHORIZED".to_string(),
                    message,
                    details: None,
                },
            ),
            AppError::Forbidden(message) => (
                StatusCode::FORBIDDEN,
                ErrorResponse {
                    error: "FORBIDDEN".to_string(),
                    message,
                    details: None,
                },
            ),
            AppError::NotFound(message) => (
                StatusCode::NOT_FOUND,
                ErrorResponse {
                    error: "NOT_FOUND".to_string(),
                    message,
                    details: None,
                },
            ),
            AppError::Validation(message) => (
                StatusCode::BAD_REQUEST,
                ErrorResponse {
                    error: "VALIDATION_ERROR".to_string(),
                    message,
                    details: None,
                },
            ),
            AppError::Database(e) => {
                tracing::error!("Database error: {:?}", e);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    ErrorResponse {
                        error: "DATABASE_ERROR".to_string(),
                        message: "A database error occurred".to_string(),
                        details: None,
                    },
                )
            }
            AppError::Encryption(message) => (
                StatusCode::INTERNAL_SERVER_ERROR,
                ErrorResponse {
                    error: "ENCRYPTION_ERROR".to_string(),
                    message,
                    details: None,
                },
            ),
            AppError::Internal(message) => {
                tracing::error!("Internal server error: {}", message);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    ErrorResponse {
                        error: "INTERNAL_SERVER_ERROR".to_string(),
                        message: "An internal server error occurred".to_string(),
                        details: None,
                    },
                )
            },
            AppError::Config(message) => {
                tracing::error!("Configuration error: {}", message);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    ErrorResponse {
                        error: "CONFIGURATION_ERROR".to_string(),
                        message: "A server configuration error occurred".to_string(),
                        details: None,
                    },
                )
            },
            AppError::NotImplemented(message) => (
                StatusCode::NOT_IMPLEMENTED,
                ErrorResponse {
                    error: "NOT_IMPLEMENTED".to_string(),
                    message,
                    details: None,
                },
            ),
        };

        (status, Json(error_response)).into_response()
    }
}

pub type Result<T> = std::result::Result<T, AppError>;

// Convenience functions for creating errors
impl AppError {
    pub fn auth(message: impl ToString) -> Self {
        Self::Auth(message.to_string())
    }

    pub fn forbidden(message: impl ToString) -> Self {
        Self::Forbidden(message.to_string())
    }

    pub fn not_found(message: impl ToString) -> Self {
        Self::NotFound(message.to_string())
    }

    pub fn validation(message: impl ToString) -> Self {
        Self::Validation(message.to_string())
    }

    pub fn encryption(message: impl ToString) -> Self {
        Self::Encryption(message.to_string())
    }

    pub fn internal(message: impl ToString) -> Self {
        Self::Internal(message.to_string())
    }

    pub fn config(message: impl ToString) -> Self {
        Self::Config(message.to_string())
    }
    
    pub fn not_implemented(message: impl ToString) -> Self {
        Self::NotImplemented(message.to_string())
    }
} 