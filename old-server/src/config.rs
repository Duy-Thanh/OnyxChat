use serde::Deserialize;
use std::env;

use crate::error::AppError;

#[derive(Debug, Clone, Deserialize)]
pub struct AppConfig {
    pub database_url: String,
    pub jwt_secret: String,
    pub jwt_expiration: i64,  // in seconds
    pub port: u16,
    pub refresh_token_expiration: i64,  // in seconds
    pub run_migrations: bool,
    pub log_level: String,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            database_url: "postgres://postgres:postgres@localhost:5432/onyxchat".to_string(),
            jwt_secret: "super_secret_key_change_in_production".to_string(),
            jwt_expiration: 86400, // 24 hours in seconds
            refresh_token_expiration: 604800, // 1 week in seconds
            port: 8080,
            run_migrations: true,
            log_level: "info".to_string(),
        }
    }
}

impl AppConfig {
    pub fn from_env() -> Self {
        Self {
            database_url: env::var("DATABASE_URL").unwrap_or_else(|_| Self::default().database_url),
            jwt_secret: env::var("JWT_SECRET").unwrap_or_else(|_| Self::default().jwt_secret),
            jwt_expiration: env::var("JWT_EXPIRATION")
                .ok()
                .and_then(|val| val.parse().ok())
                .unwrap_or_else(|| Self::default().jwt_expiration),
            refresh_token_expiration: env::var("REFRESH_TOKEN_EXPIRATION")
                .ok()
                .and_then(|val| val.parse().ok())
                .unwrap_or_else(|| Self::default().refresh_token_expiration),
            port: env::var("PORT")
                .ok()
                .and_then(|val| val.parse().ok())
                .unwrap_or_else(|| Self::default().port),
            run_migrations: env::var("RUN_MIGRATIONS")
                .ok()
                .and_then(|val| val.parse().ok())
                .unwrap_or_else(|| Self::default().run_migrations),
            log_level: env::var("LOG_LEVEL")
                .unwrap_or_else(|_| Self::default().log_level),
        }
    }
} 