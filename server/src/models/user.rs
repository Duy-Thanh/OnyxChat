use serde::{Deserialize, Serialize};
use sqlx::{FromRow, Pool, Postgres};
use uuid::Uuid;
use chrono::{DateTime, Utc};
use validator::Validate;

use crate::error::{AppError, Result};

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct User {
    pub id: Uuid,
    pub username: String,
    pub email: String,
    #[serde(skip_serializing)]
    pub password_hash: String,
    pub display_name: Option<String>,
    pub bio: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub last_active_at: Option<DateTime<Utc>>,
    pub is_active: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct UserProfile {
    pub id: Uuid,
    pub username: String,
    pub display_name: Option<String>,
    pub bio: Option<String>,
    pub created_at: DateTime<Utc>,
    pub last_active_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct CreateUserRequest {
    #[validate(length(min = 3, max = 50))]
    pub username: String,
    
    #[validate(email)]
    pub email: String,
    
    #[validate(length(min = 8))]
    pub password: String,
    
    pub display_name: Option<String>,
    pub bio: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct UpdateUserRequest {
    #[validate(email)]
    pub email: Option<String>,
    
    pub display_name: Option<String>,
    pub bio: Option<String>,
    
    #[validate(length(min = 8))]
    pub password: Option<String>,
}

impl User {
    pub async fn find_by_id(pool: &Pool<Postgres>, id: Uuid) -> Result<Self> {
        sqlx::query_as!(
            User,
            r#"
            SELECT * FROM users WHERE id = $1
            "#,
            id
        )
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::not_found(format!("User with ID {} not found", id)))
    }

    pub async fn find_by_username(pool: &Pool<Postgres>, username: &str) -> Result<Self> {
        sqlx::query_as!(
            User,
            r#"
            SELECT * FROM users WHERE username = $1
            "#,
            username
        )
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::not_found(format!("User with username {} not found", username)))
    }

    pub async fn find_by_email(pool: &Pool<Postgres>, email: &str) -> Result<Self> {
        sqlx::query_as!(
            User,
            r#"
            SELECT * FROM users WHERE email = $1
            "#,
            email
        )
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::not_found(format!("User with email {} not found", email)))
    }

    pub async fn create(pool: &Pool<Postgres>, user: &CreateUserRequest, password_hash: &str) -> Result<Self> {
        sqlx::query_as!(
            User,
            r#"
            INSERT INTO users (username, email, password_hash, display_name, bio)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING *
            "#,
            user.username,
            user.email,
            password_hash,
            user.display_name,
            user.bio
        )
        .fetch_one(pool)
        .await
        .map_err(|e| match e {
            sqlx::Error::Database(ref db_error) if db_error.is_unique_violation() => {
                if db_error.message().contains("users_username_key") {
                    AppError::validation(format!("Username '{}' is already taken", user.username))
                } else if db_error.message().contains("users_email_key") {
                    AppError::validation(format!("Email '{}' is already registered", user.email))
                } else {
                    AppError::Database(e)
                }
            }
            _ => AppError::Database(e),
        })
    }

    pub async fn update(
        pool: &Pool<Postgres>,
        id: Uuid,
        update: &UpdateUserRequest,
        password_hash: Option<&str>,
    ) -> Result<Self> {
        // Build query dynamically based on provided fields
        let mut query = String::from("UPDATE users SET updated_at = NOW()");
        let mut params = Vec::new();
        
        if update.email.is_some() {
            query.push_str(", email = $1");
            params.push(update.email.as_ref().unwrap());
        }
        
        if update.display_name.is_some() {
            query.push_str(", display_name = $2");
            params.push(update.display_name.as_ref().unwrap());
        }
        
        if update.bio.is_some() {
            query.push_str(", bio = $3");
            params.push(update.bio.as_ref().unwrap());
        }
        
        if password_hash.is_some() {
            query.push_str(", password_hash = $4");
            params.push(password_hash.unwrap());
        }
        
        query.push_str(" WHERE id = $5 RETURNING *");
        
        // Build and execute query with parameters
        sqlx::query_as::<_, User>(&query)
            .bind_all(params)
            .bind(id)
            .fetch_one(pool)
            .await
            .map_err(|e| match e {
                sqlx::Error::Database(ref db_error) if db_error.is_unique_violation() => {
                    if let Some(email) = &update.email {
                        if db_error.message().contains("users_email_key") {
                            return AppError::validation(format!("Email '{}' is already registered", email));
                        }
                    }
                    AppError::Database(e)
                }
                _ => AppError::Database(e),
            })
    }

    pub async fn update_last_active(pool: &Pool<Postgres>, id: Uuid) -> Result<()> {
        sqlx::query!(
            r#"
            UPDATE users
            SET last_active_at = NOW()
            WHERE id = $1
            "#,
            id
        )
        .execute(pool)
        .await?;

        Ok(())
    }

    pub fn to_profile(&self) -> UserProfile {
        UserProfile {
            id: self.id,
            username: self.username.clone(),
            display_name: self.display_name.clone(),
            bio: self.bio.clone(),
            created_at: self.created_at,
            last_active_at: self.last_active_at,
        }
    }
} 