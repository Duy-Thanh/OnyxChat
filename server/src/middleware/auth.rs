use std::convert::Infallible;

use axum::{
    async_trait,
    extract::{FromRef, FromRequestParts, State},
    http::{request::Parts, StatusCode},
    response::{IntoResponse, Response},
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use jsonwebtoken::{decode, DecodingKey, Validation};
use serde::{Deserialize, Serialize};
use thiserror::Error;

use crate::{
    error::AppError,
    models::{auth::AuthService, AppState},
    config::AppConfig,
};

// Auth specific errors that can be used by other modules
#[derive(Debug, Error)]
pub enum AuthError {
    #[error("Invalid token")]
    InvalidToken,
    
    #[error("Token expired")]
    TokenExpired,
    
    #[error("Invalid password")]
    InvalidPassword,
    
    #[error("Unauthorized")]
    Unauthorized,
}

// Implementation for displaying auth errors
impl std::fmt::Display for AuthError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let message = match self {
            AuthError::InvalidToken => "Invalid token",
            AuthError::TokenExpired => "Token has expired",
            AuthError::InvalidPassword => "Invalid password",
            AuthError::Unauthorized => "Unauthorized",
        };
        write!(f, "{}", message)
    }
}

// Implement std::error::Error for AuthError
impl std::error::Error for AuthError {}

#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    pub sub: String,       // Subject (user ID)
    pub username: String,  // Username
    pub exp: usize,        // Expiration time
    pub iat: usize,        // Issued at
}

#[derive(Debug, Clone)]
pub struct CurrentUser {
    pub id: String,
    pub username: String,
}

#[async_trait]
impl<S> FromRequestParts<S> for CurrentUser
where
    S: Send + Sync,
{
    type Rejection = Response;

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        // Extract the token from the Authorization header
        let TypedHeader(Authorization(bearer)) = 
            TypedHeader::<Authorization<Bearer>>::from_request_parts(parts, _state)
                .await
                .map_err(|_| {
                    let json = serde_json::json!({
                        "status": "error",
                        "message": "Missing or invalid authorization header"
                    });
                    (StatusCode::UNAUTHORIZED, serde_json::to_string(&json).unwrap()).into_response()
                })?;

        // Decode and validate the token
        let token_data = decode::<Claims>(
            bearer.token(),
            &DecodingKey::from_secret("super_secret_key_change_in_production".as_bytes()),
            &Validation::default(),
        )
        .map_err(|_| {
            let json = serde_json::json!({
                "status": "error",
                "message": "Invalid token"
            });
            (StatusCode::UNAUTHORIZED, serde_json::to_string(&json).unwrap()).into_response()
        })?;

        // Extract user information
        let user = CurrentUser {
            id: token_data.claims.sub,
            username: token_data.claims.username,
        };

        Ok(user)
    }
}

// Convenience function to check if a token is valid
pub fn validate_token(token: &str) -> Result<Claims, AppError> {
    let token_data = decode::<Claims>(
        token,
        &DecodingKey::from_secret("super_secret_key_change_in_production".as_bytes()), 
        &Validation::default(),
    )
    .map_err(|_| AppError::unauthorized("Invalid token"))?;

    Ok(token_data.claims)
}

// Optional version that doesn't require authentication
#[derive(Debug, Clone)]
pub struct OptionalUser {
    pub user: Option<CurrentUser>,
}

#[async_trait]
impl<S> FromRequestParts<S> for OptionalUser
where
    AppState: FromRef<S>,
    S: Send + Sync,
{
    type Rejection = Infallible;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        // Try to extract the user, but don't fail if not possible
        let user = CurrentUser::from_request_parts(parts, state).await.ok();
        Ok(OptionalUser { user })
    }
}

// Middleware for routes that need authentication
pub async fn auth_middleware<B>(
    State(state): State<AppState>,
    // Try to extract the token from the Authorization header
    typed_header: Option<TypedHeader<Authorization<Bearer>>>,
    mut request: Request<B>,
    next: Next<B>,
) -> Result<Response, AppError> {
    // Public routes that don't require authentication
    let path = request.uri().path();
    if path == "/api/health" || 
       path == "/api/hello" || 
       path == "/api/auth/login" || 
       path.starts_with("/ws/") ||
       (path == "/api/users" && request.method() == axum::http::Method::POST) {
        return Ok(next.run(request).await);
    }
    
    // For protected routes, require authentication
    if let Some(TypedHeader(Authorization(bearer))) = typed_header {
        match AuthService::validate_token(bearer.token()) {
            Ok(claims) => {
                // Add current user to request extensions
                let current_user = CurrentUser {
                    id: claims.sub,
                    username: claims.username,
                };
                request.extensions_mut().insert(current_user);
                return Ok(next.run(request).await);
            },
            Err(e) => {
                return Err(AppError::auth("Invalid or expired token"));
            }
        }
    }
    
    // No token provided
    Err(AppError::auth("Authentication required"))
}

// Extractor for the current user
#[axum::async_trait]
impl<S> axum::extract::FromRequestParts<S> for CurrentUser 
where
    S: Send + Sync,
{
    type Rejection = AppError;
    
    async fn from_request_parts(
        parts: &mut axum::http::request::Parts,
        _state: &S,
    ) -> Result<Self, Self::Rejection> {
        parts
            .extensions
            .get::<CurrentUser>()
            .cloned()
            .ok_or_else(|| AppError::auth("Unauthorized: Missing authentication"))
    }
} 