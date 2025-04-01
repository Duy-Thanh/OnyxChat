use serde::{Deserialize, Serialize};
use sqlx::{FromRow, Pool, Postgres};
use uuid::Uuid;
use chrono::{DateTime, Utc};
use validator::Validate;

use crate::error::{AppError, Result};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct User {
    pub id: String,
    pub username: String,
    pub email: String,
    #[serde(skip_serializing)]
    pub password_hash: String,
    pub created_at: String,
}

#[derive(Debug, Serialize)]
pub struct UserProfile {
    pub id: String,
    pub username: String,
}

#[derive(Debug, Deserialize, Validate)]
pub struct CreateUserRequest {
    #[validate(length(min = 3, max = 50))]
    pub username: String,
    
    #[validate(email)]
    pub email: String,
    
    #[validate(length(min = 8, max = 100))]
    pub password: String,
}

#[derive(Debug, Deserialize, Validate)]
pub struct UpdateUserRequest {
    #[validate(length(min = 3, max = 50))]
    pub username: Option<String>,
    
    #[validate(email)]
    pub email: Option<String>,
    
    #[validate(length(min = 8, max = 100))]
    pub password: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct UserCreatedResponse {
    pub id: String,
    pub username: String,
    pub email: String,
}

impl User {
    pub async fn find_by_id(pool: &Pool<Postgres>, id: Uuid) -> Result<Self> {
        // For development, return a mock user
        Ok(Self {
            id: id.to_string(),
            username: "user123".to_string(),
            email: "user123@example.com".to_string(),
            password_hash: "hashed_password".to_string(),
            created_at: Utc::now().to_rfc3339(),
        })
    }

    pub async fn find_by_username(pool: &Pool<Postgres>, username: &str) -> Result<Self> {
        // In a real implementation, this would query the database
        // For this demo version, we'll create a mock user
        let user = User {
            id: Uuid::new_v4().to_string(),
            username: username.to_string(),
            email: format!("{}@example.com", username),
            password_hash: "hashed_password".to_string(),
            created_at: chrono::Utc::now().to_rfc3339(),
        };
        
        Ok(user)
    }

    pub async fn find_by_email(pool: &Pool<Postgres>, email: &str) -> Result<Self> {
        // In a real implementation, this would query the database
        // For this demo version, we'll create a mock user
        let user = User {
            id: Uuid::new_v4().to_string(),
            username: email.split('@').next().unwrap_or("user").to_string(),
            email: email.to_string(),
            password_hash: "hashed_password".to_string(),
            created_at: chrono::Utc::now().to_rfc3339(),
        };
        
        Ok(user)
    }

    pub async fn create(pool: &Pool<Postgres>, user: &CreateUserRequest, password_hash: &str) -> Result<Self> {
        // In a real implementation, this would insert into the database
        // For this demo version, we'll create a mock user
        let user = User {
            id: Uuid::new_v4().to_string(),
            username: user.username.clone(),
            email: user.email.clone(),
            password_hash: password_hash.to_string(),
            created_at: chrono::Utc::now().to_rfc3339(),
        };
        
        Ok(user)
    }

    pub async fn update(
        pool: &Pool<Postgres>,
        id: String,
        update: &UpdateUserRequest,
        password_hash: Option<&str>,
    ) -> Result<Self> {
        // In a real implementation, this would update the database
        // For this demo version, we'll create a mock updated user
        let mut user = Self::find_by_id(pool, Uuid::parse_str(&id)?).await?;
        
        if let Some(username) = &update.username {
            user.username = username.clone();
        }
        
        if let Some(email) = &update.email {
            user.email = email.clone();
        }
        
        if let Some(password) = password_hash {
            user.password_hash = password.to_string();
        }
        
        Ok(user)
    }

    pub async fn update_last_active(pool: &Pool<Postgres>, id: String) -> Result<()> {
        // In a real implementation, this would update the last_active_at field
        // For this demo version, we'll just return Ok
        Ok(())
    }

    pub fn to_profile(&self) -> UserProfile {
        UserProfile {
            id: self.id.clone(),
            username: self.username.clone(),
        }
    }
} 