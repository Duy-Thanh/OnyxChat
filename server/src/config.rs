use serde::Deserialize;
use std::env;

use crate::error::AppError;

#[derive(Debug, Clone, Deserialize)]
pub struct AppConfig {
    pub database_url: String,
    pub jwt_secret: String,
    pub jwt_expiration: i64,  // in seconds
    pub refresh_token_expiration: i64,  // in seconds
    pub port: u16,
    pub run_migrations: bool,
    pub log_level: String,
}

impl AppConfig {
    pub fn load() -> Result<Self, AppError> {
        let database_url = env::var("DATABASE_URL")
            .map_err(|_| AppError::config("DATABASE_URL environment variable is not set"))?;
        
        let jwt_secret = env::var("JWT_SECRET")
            .map_err(|_| AppError::config("JWT_SECRET environment variable is not set"))?;
        
        let jwt_expiration = env::var("JWT_EXPIRATION")
            .unwrap_or_else(|_| "3600".to_string())  // 1 hour default
            .parse::<i64>()
            .map_err(|_| AppError::config("Invalid JWT_EXPIRATION value"))?;
        
        let refresh_token_expiration = env::var("REFRESH_TOKEN_EXPIRATION")
            .unwrap_or_else(|_| "604800".to_string())  // 1 week default
            .parse::<i64>()
            .map_err(|_| AppError::config("Invalid REFRESH_TOKEN_EXPIRATION value"))?;
        
        let port = env::var("PORT")
            .unwrap_or_else(|_| "8000".to_string())
            .parse::<u16>()
            .map_err(|_| AppError::config("Invalid PORT value"))?;
        
        let run_migrations = env::var("RUN_MIGRATIONS")
            .unwrap_or_else(|_| "true".to_string())
            .parse::<bool>()
            .map_err(|_| AppError::config("Invalid RUN_MIGRATIONS value"))?;
        
        let log_level = env::var("LOG_LEVEL")
            .unwrap_or_else(|_| "info".to_string());

        Ok(Self {
            database_url,
            jwt_secret,
            jwt_expiration,
            refresh_token_expiration,
            port,
            run_migrations,
            log_level,
        })
    }
} 