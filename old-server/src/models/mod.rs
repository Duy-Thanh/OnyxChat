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

// Mock implementations when using mock_db feature
#[cfg(feature = "mock_db")]
pub mod mocks {
    use crate::error::Result;
    use uuid::Uuid;
    use chrono::Utc;
    
    impl crate::models::user::User {
        pub async fn find_by_id(_pool: &sqlx::PgPool, id: &str) -> Result<Self> {
            Ok(Self {
                id: id.to_string(),
                username: "mock_user".to_string(),
                email: "mock@example.com".to_string(),
                password_hash: "mock_hash".to_string(),
                display_name: Some("Mock User".to_string()),
                created_at: Utc::now(),
                updated_at: Utc::now(),
                last_active_at: Some(Utc::now()),
                is_active: true,
            })
        }

        pub async fn find_by_username(_pool: &sqlx::PgPool, username: &str) -> Result<Self> {
            Ok(Self {
                id: Uuid::new_v4().to_string(),
                username: username.to_string(),
                email: format!("{}@example.com", username),
                password_hash: "mock_hash".to_string(),
                display_name: None,
                created_at: Utc::now(),
                updated_at: Utc::now(),
                last_active_at: Some(Utc::now()),
                is_active: true,
            })
        }

        pub async fn update(
            _pool: &sqlx::PgPool,
            id: &str,
            _request: &crate::models::user::UpdateUserRequest,
            _update_by: Option<String>,
        ) -> Result<Self> {
            Self::find_by_id(_pool, id).await
        }
    }
}

// Don't define another AppState here, use the one from the crate root
// If a mod needs AppState, it should import it from crate::AppState 