// Re-export all modules for use in binaries

pub mod models;
pub mod handlers;
pub mod middleware;
pub mod ws;
pub mod error;
pub mod config;

use std::sync::Arc;
use sqlx::postgres::PgPool;

// Export common types
pub use crate::models::{
    User, Message, LoginRequest, AuthResponse, 
    UserCreatedResponse, MessageResponse, AuthService
};
pub use crate::middleware::auth::{CurrentUser, auth_middleware};
pub use crate::error::{AppError, Result};
pub use crate::middleware::auth::AuthError;
pub use crate::ws::WebSocketManager;

// Application state
#[derive(Clone)]
pub struct AppState {
    pub db: Option<PgPool>,
    pub config: Arc<config::AppConfig>,
    pub ws_manager: Arc<WebSocketManager>,
}

impl AppState {
    pub fn new(db: Option<PgPool>, ws_manager: Arc<WebSocketManager>) -> Self {
        Self {
            db,
            config: Arc::new(config::AppConfig::from_env()),
            ws_manager,
        }
    }
} 