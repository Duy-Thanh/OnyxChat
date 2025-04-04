use argon2::{
    password_hash::{rand_core::OsRng, PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Argon2,
};
use axum::{
    async_trait,
    extract::FromRequestParts,
    http::{request::Parts, StatusCode},
    Json,
};
use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use once_cell::sync::Lazy;
use serde::{Deserialize, Serialize};
use std::fmt;

// JWT constants - in a real app, these would be in environment variables
static JWT_SECRET: Lazy<String> = Lazy::new(|| "your_jwt_secret_key".to_string());
static JWT_EXPIRY: Lazy<Duration> = Lazy::new(|| Duration::hours(24));

// JWT claim structure
#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    pub sub: String,         // Subject (user ID)
    pub exp: usize,          // Expiration time
    pub iat: usize,          // Issued at
    pub username: String,    // Username
}

// Authentication error types
#[derive(Debug)]
pub enum AuthError {
    InvalidToken,
    MissingToken,
    TokenCreation,
    PasswordHashing,
    InvalidPassword,
}

impl fmt::Display for AuthError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AuthError::InvalidToken => write!(f, "Invalid token"),
            AuthError::MissingToken => write!(f, "Missing token"),
            AuthError::TokenCreation => write!(f, "Failed to create token"),
            AuthError::PasswordHashing => write!(f, "Failed to hash password"),
            AuthError::InvalidPassword => write!(f, "Invalid password"),
        }
    }
}

// Auth service for token creation and validation
pub struct AuthService;

impl AuthService {
    // Hash a password using Argon2
    pub fn hash_password(password: &str) -> Result<String, AuthError> {
        let salt = SaltString::generate(&mut OsRng);
        let argon2 = Argon2::default();
        
        argon2
            .hash_password(password.as_bytes(), &salt)
            .map(|hash| hash.to_string())
            .map_err(|_| AuthError::PasswordHashing)
    }
    
    // Verify a password against a hash
    pub fn verify_password(password: &str, hash: &str) -> Result<bool, AuthError> {
        let parsed_hash = PasswordHash::new(hash).map_err(|_| AuthError::InvalidPassword)?;
        
        Ok(Argon2::default()
            .verify_password(password.as_bytes(), &parsed_hash)
            .is_ok())
    }
    
    // Create a JWT token
    pub fn create_token(user_id: &str, username: &str) -> Result<String, AuthError> {
        let now = Utc::now();
        let expires_at = now + *JWT_EXPIRY;
        
        let claims = Claims {
            sub: user_id.to_string(),
            exp: expires_at.timestamp() as usize,
            iat: now.timestamp() as usize,
            username: username.to_string(),
        };
        
        encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(JWT_SECRET.as_bytes()),
        )
        .map_err(|_| AuthError::TokenCreation)
    }
    
    // Validate a JWT token
    pub fn validate_token(token: &str) -> Result<Claims, AuthError> {
        decode::<Claims>(
            token,
            &DecodingKey::from_secret(JWT_SECRET.as_bytes()),
            &Validation::default(),
        )
        .map(|data| data.claims)
        .map_err(|_| AuthError::InvalidToken)
    }
}

// Authentication response
#[derive(Debug, Serialize)]
pub struct AuthResponse {
    pub access_token: String,
    pub token_type: &'static str,
    pub expires_in: i64,
}

// Login request
#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub username: String,
    pub password: String,
}

// CurrentUser extractor for protected routes
pub struct CurrentUser {
    pub user_id: String,
    pub username: String,
}

#[async_trait]
impl<S> FromRequestParts<S> for CurrentUser
where
    S: Send + Sync,
{
    type Rejection = (StatusCode, Json<serde_json::Value>);

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        // Get the Authorization header
        let auth_header = parts
            .headers
            .get("Authorization")
            .and_then(|header| header.to_str().ok());

        let auth_header = match auth_header {
            Some(header) => header,
            None => {
                return Err((
                    StatusCode::UNAUTHORIZED,
                    Json(serde_json::json!({
                        "error": "Missing authorization header"
                    })),
                ))
            }
        };

        // Check the format (Bearer token)
        if !auth_header.starts_with("Bearer ") {
            return Err((
                StatusCode::UNAUTHORIZED,
                Json(serde_json::json!({
                    "error": "Invalid authorization header format"
                })),
            ));
        }

        // Extract the token
        let token = &auth_header[7..];

        // Validate the token
        match AuthService::validate_token(token) {
            Ok(claims) => Ok(CurrentUser {
                user_id: claims.sub,
                username: claims.username,
            }),
            Err(_) => Err((
                StatusCode::UNAUTHORIZED,
                Json(serde_json::json!({
                    "error": "Invalid token"
                })),
            )),
        }
    }
} 