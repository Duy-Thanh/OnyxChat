pub mod auth;
pub mod user;
pub mod message;
pub mod crypto;

pub use auth::*;
pub use user::*;
pub use message::*;
pub use crypto::*;

use sqlx::{Pool, Postgres};
use std::sync::Arc;

use crate::config::AppConfig;

// Application state to be shared with all routes
#[derive(Clone)]
pub struct AppState {
    pub db: Pool<Postgres>,
    pub config: Arc<AppConfig>,
}

impl AppState {
    pub fn new(db: Pool<Postgres>, config: AppConfig) -> Self {
        AppState {
            db,
            config: Arc::new(config),
        }
    }
} 