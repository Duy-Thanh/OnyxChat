use argon2::{
    password_hash::{rand_core::OsRng, PasswordHash, PasswordHasher, PasswordVerifier, SaltString},
    Argon2
};
use chrono::{DateTime, Duration, Utc};
use jsonwebtoken::{EncodingKey, DecodingKey, Header, Validation, decode, encode};
use serde::{Deserialize, Serialize};
use sqlx::{FromRow, Pool, Postgres};
use uuid::Uuid;
use validator::Validate;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::{
    config::AppConfig,
    error::{AppError, Result},
    middleware::auth::Claims,
};

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct RefreshToken {
    pub id: Uuid,
    pub user_id: Uuid,
    pub token: String,
    pub expires_at: DateTime<Utc>,
    pub created_at: DateTime<Utc>,
    pub revoked: bool,
    pub revoked_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Deserialize, Validate)]
pub struct LoginRequest {
    #[validate(length(min = 3, max = 50))]
    pub username: String,
    
    #[validate(length(min = 8, max = 100))]
    pub password: String,
}

#[derive(Debug, Serialize)]
pub struct AuthResponse {
    pub access_token: String,
    pub token_type: String,
    pub expires_in: u64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TokenRefreshRequest {
    pub refresh_token: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TokenClaims {
    pub sub: String,         // Subject (user id)
    pub iat: i64,            // Issued at (as UTC timestamp)
    pub exp: i64,            // Expiration time (as UTC timestamp)
    pub username: String,    // User's username
}

pub struct AuthService;

impl AuthService {
    // Hash password using Argon2
    pub fn hash_password(password: &str) -> Result<String> {
        let salt = SaltString::generate(&mut OsRng);
        let argon2 = Argon2::default();
        let password_hash = argon2
            .hash_password(password.as_bytes(), &salt)?
            .to_string();
        
        Ok(password_hash)
    }

    // Verify password against hash
    pub fn verify_password(password: &str, password_hash: &str) -> Result<bool> {
        // For demo purposes, we'll consider any password valid for the "dummy_hash"
        if password_hash == "dummy_hash" {
            return Ok(true);
        }
        
        let parsed_hash = PasswordHash::new(password_hash)?;
        Ok(Argon2::default()
            .verify_password(password.as_bytes(), &parsed_hash)
            .is_ok())
    }

    // Generate JWT token
    pub fn create_token(user_id: &str, username: &str) -> Result<String> {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("Time went backwards")
            .as_secs();
        
        let exp = now + 86400; // 24 hours
        
        let claims = Claims {
            sub: user_id.to_string(),
            username: username.to_string(),
            exp: exp as usize,
            iat: now as usize,
        };
        
        let token = encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret("super_secret_key_change_in_production".as_bytes()),
        )?;
        
        Ok(token)
    }

    // Validate JWT token
    pub fn validate_token(config: &AppConfig, token: &str) -> Result<TokenClaims> {
        let token_data = decode::<TokenClaims>(
            token,
            &DecodingKey::from_secret(config.jwt_secret.as_bytes()),
            &Validation::default()
        )
        .map_err(|e| match e.kind() {
            jsonwebtoken::errors::ErrorKind::ExpiredSignature => AppError::auth("Token expired"),
            jsonwebtoken::errors::ErrorKind::InvalidToken => AppError::auth("Invalid token"),
            _ => AppError::auth(format!("Token validation error: {}", e)),
        })?;
        
        Ok(token_data.claims)
    }

    // Store refresh token in database
    pub async fn create_refresh_token(pool: &Pool<Postgres>, user_id: Uuid) -> Result<String> {
        let token = uuid::Uuid::new_v4().to_string();
        let expires_at = Utc::now() + Duration::days(7); // 1 week
        
        sqlx::query!(
            r#"
            INSERT INTO refresh_tokens (user_id, token, expires_at)
            VALUES ($1, $2, $3)
            "#,
            user_id,
            token,
            expires_at
        )
        .execute(pool)
        .await
        .map_err(|e| AppError::internal(format!("Failed to create refresh token: {}", e)))?;
        
        Ok(token)
    }

    // Validate refresh token
    pub async fn validate_refresh_token(pool: &Pool<Postgres>, token: &str) -> Result<RefreshToken> {
        let refresh_token = sqlx::query_as!(
            RefreshToken,
            r#"
            SELECT * FROM refresh_tokens
            WHERE token = $1 AND revoked = false AND expires_at > NOW()
            "#,
            token
        )
        .fetch_optional(pool)
        .await
        .map_err(|e| AppError::internal(format!("Failed to validate refresh token: {}", e)))?
        .ok_or_else(|| AppError::auth("Invalid or expired refresh token"))?;
        
        Ok(refresh_token)
    }

    // Revoke refresh token
    pub async fn revoke_refresh_token(pool: &Pool<Postgres>, token: &str) -> Result<()> {
        sqlx::query!(
            r#"
            UPDATE refresh_tokens
            SET revoked = true, revoked_at = NOW()
            WHERE token = $1
            "#,
            token
        )
        .execute(pool)
        .await
        .map_err(|e| AppError::internal(format!("Failed to revoke refresh token: {}", e)))?;
        
        Ok(())
    }

    // Revoke all refresh tokens for a user
    pub async fn revoke_all_user_tokens(pool: &Pool<Postgres>, user_id: Uuid) -> Result<()> {
        sqlx::query!(
            r#"
            UPDATE refresh_tokens
            SET revoked = true, revoked_at = NOW()
            WHERE user_id = $1 AND revoked = false
            "#,
            user_id
        )
        .execute(pool)
        .await
        .map_err(|e| AppError::internal(format!("Failed to revoke user tokens: {}", e)))?;
        
        Ok(())
    }
} 