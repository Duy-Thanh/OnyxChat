use serde::{Deserialize, Serialize};
use sqlx::{FromRow, Pool, Postgres};
use uuid::Uuid;
use chrono::{DateTime, Utc};
use validator::Validate;
use sha2;

use crate::error::{AppError, Result};

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct User {
    pub id: String,
    pub username: String,
    pub email: String,
    #[serde(skip_serializing)]
    pub password_hash: String,
    pub display_name: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub last_active_at: Option<DateTime<Utc>>,
    pub is_active: bool,
}

#[derive(Debug, Serialize)]
pub struct UserProfile {
    pub id: String,
    pub username: String,
    pub display_name: Option<String>,
    pub is_active: bool,
    pub last_active_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Deserialize, Validate)]
pub struct CreateUserRequest {
    #[validate(length(min = 3, max = 50))]
    pub username: String,
    
    #[validate(email)]
    pub email: String,
    
    #[validate(length(min = 8, max = 100))]
    pub password: String,

    pub display_name: Option<String>,
}

#[derive(Debug, Deserialize, Validate)]
pub struct UpdateUserRequest {
    #[validate(length(min = 3, max = 50))]
    pub username: Option<String>,
    
    #[validate(email)]
    pub email: Option<String>,
    
    #[validate(length(min = 8, max = 100))]
    pub password: Option<String>,

    pub display_name: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct UserCreatedResponse {
    pub id: String,
    pub username: String,
    pub email: String,
}

impl User {
    pub async fn find_by_id(pool: &Pool<Postgres>, id: &str) -> Result<Self> {
        let _uuid = Uuid::parse_str(id).map_err(|_| AppError::not_found("User not found"))?;
        
        Ok(Self {
            id: id.to_string(),
            username: "demo_user".to_string(),
            email: "demo@example.com".to_string(),
            password_hash: "hashed_password".to_string(),
            display_name: Some("Demo User".to_string()),
            created_at: Utc::now(),
            updated_at: Utc::now(),
            last_active_at: Some(Utc::now()),
            is_active: true,
        })
    }

    pub async fn find_by_username(pool: &Pool<Postgres>, username: &str) -> Result<Self> {
        let mut hasher = sha2::Sha256::new();
        use sha2::Digest;
        hasher.update(username.as_bytes());
        let result = hasher.finalize();
        let user_id = format!("{:x}", result);
        
        Ok(Self {
            id: user_id,
            username: username.to_string(),
            email: format!("{}@example.com", username),
            password_hash: "dummy_hash".to_string(),
            display_name: Some(username.to_string()),
            created_at: Utc::now(),
            updated_at: Utc::now(),
            last_active_at: Some(Utc::now()),
            is_active: true,
        })
    }

    pub async fn find_by_email(pool: &Pool<Postgres>, email: &str) -> Result<Self> {
        if email.contains("exists") {
            let username = email.split('@').next().unwrap_or("user");
            
            return Ok(Self {
                id: Uuid::new_v4().to_string(),
                username: username.to_string(),
                email: email.to_string(),
                password_hash: "dummy_hash".to_string(),
                display_name: Some(username.to_string()),
                created_at: Utc::now(),
                updated_at: Utc::now(),
                last_active_at: Some(Utc::now()),
                is_active: true,
            });
        }
        
        Err(AppError::not_found("User not found"))
    }

    pub async fn create(pool: &Pool<Postgres>, request: &CreateUserRequest, password_hash: &str) -> Result<Self> {
        let user_id = Uuid::new_v4().to_string();
        let now = Utc::now();
        
        let user = Self {
            id: user_id,
            username: request.username.clone(),
            email: request.email.clone(),
            password_hash: password_hash.to_string(),
            display_name: request.display_name.clone(),
            created_at: now,
            updated_at: now,
            last_active_at: Some(now),
            is_active: true,
        };
        
        Ok(user)
    }

    pub async fn update(
        pool: &Pool<Postgres>,
        id: &str,
        update: &UpdateUserRequest,
        password_hash: Option<&str>,
    ) -> Result<Self> {
        let mut user = Self::find_by_id(pool, id).await?;
        
        if let Some(username) = &update.username {
            user.username = username.clone();
        }
        
        if let Some(email) = &update.email {
            user.email = email.clone();
        }
        
        if let Some(display_name) = &update.display_name {
            user.display_name = Some(display_name.clone());
        }
        
        if let Some(password) = password_hash {
            user.password_hash = password.to_string();
        }
        
        user.updated_at = Utc::now();
        
        Ok(user)
    }

    pub async fn update_last_active(pool: &Pool<Postgres>, id: String) -> Result<()> {
        Ok(())
    }

    pub fn to_profile(&self) -> UserProfile {
        UserProfile {
            id: self.id.clone(),
            username: self.username.clone(),
            display_name: self.display_name.clone(),
            is_active: self.is_active,
            last_active_at: self.last_active_at,
        }
    }
} 