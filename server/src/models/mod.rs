pub mod user;
pub mod message;
pub mod auth;
pub mod crypto;

pub use user::*;
pub use message::*;
pub use auth::*;
pub use crypto::*;

// Re-export common structs
pub use user::User;
pub use message::Message;
pub use auth::{AuthService, LoginRequest, AuthResponse};

use sqlx::{Pool, Postgres};
use std::sync::Arc;

use crate::config::AppConfig;
use crate::ws::WebSocketManager;

// Application state to be shared with all routes
#[derive(Clone)]
pub struct AppState {
    pub db: Pool<Postgres>,
    pub config: Arc<AppConfig>,
    pub ws_manager: Arc<WebSocketManager>,
}

impl AppState {
    pub fn new(db: Option<Pool<Postgres>>, ws_manager: Arc<WebSocketManager>) -> Self {
        // For now, use a mock DB if none provided
        // In production, this would be an actual DB connection
        let db = db.unwrap_or_else(|| {
            panic!("Database connection is required")
        });
        
        // Use default config if not provided
        let config = AppConfig::default();
        
        AppState {
            db,
            config: Arc::new(config),
            ws_manager,
        }
    }
} 