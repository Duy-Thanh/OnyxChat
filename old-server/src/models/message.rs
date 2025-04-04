use serde::{Deserialize, Serialize};
use sqlx::{FromRow, Pool, Postgres};
use uuid::Uuid;
use chrono::{DateTime, Utc};
use validator::Validate;

use crate::error::{AppError, Result};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Message {
    pub id: String,
    pub sender_id: String,
    pub recipient_id: String,
    pub content: String,
    pub sent_at: String,
    pub received_at: Option<String>,
    pub read_at: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct SendMessageRequest {
    pub recipient_id: String,
    pub content: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct MessageResponse {
    pub id: String,
    pub sender_id: String,
    pub recipient_id: String,
    pub content: String,
    pub sent_at: String,
    pub received_at: Option<String>,
    pub read_at: Option<String>,
}

impl Message {
    pub async fn create(
        pool: &Pool<Postgres>,
        sender_id: Uuid,
        recipient_id: Uuid,
        encrypted_content: &str,
        iv: &str,
    ) -> Result<Self> {
        #[cfg(feature = "mock")]
        {
            let message = Message {
                id: Uuid::new_v4().to_string(),
                sender_id: sender_id.to_string(),
                recipient_id: recipient_id.to_string(),
                content: encrypted_content.to_string(),
                sent_at: chrono::Utc::now().to_rfc3339(),
                received_at: None,
                read_at: None,
            };
            
            return Ok(message);
        }

        #[cfg(not(feature = "mock"))]
        sqlx::query_as!(
            Message,
            r#"
            INSERT INTO messages (sender_id, recipient_id, encrypted_content, iv)
            VALUES ($1, $2, $3, $4)
            RETURNING *
            "#,
            sender_id,
            recipient_id,
            encrypted_content,
            iv
        )
        .fetch_one(pool)
        .await
        .map_err(|e| AppError::internal(format!("Failed to create message: {}", e)))
    }

    pub async fn create_simplified(
        pool: &Pool<Postgres>,
        sender_id: String,
        recipient_id: Uuid,
        content: &str,
    ) -> Result<Self> {
        let message = Message {
            id: Uuid::new_v4().to_string(),
            sender_id,
            recipient_id: recipient_id.to_string(),
            content: content.to_string(),
            sent_at: chrono::Utc::now().to_rfc3339(),
            received_at: None,
            read_at: None,
        };
        
        Ok(message)
    }

    pub async fn find_by_id(pool: &Pool<Postgres>, id: Uuid) -> Result<Self> {
        #[cfg(feature = "mock")]
        {
            let message = Message {
                id: id.to_string(),
                sender_id: Uuid::new_v4().to_string(),
                recipient_id: Uuid::new_v4().to_string(),
                content: "Mock message content".to_string(),
                sent_at: chrono::Utc::now().to_rfc3339(),
                received_at: None,
                read_at: None,
            };
            
            return Ok(message);
        }

        #[cfg(not(feature = "mock"))]
        sqlx::query_as!(
            Message,
            r#"
            SELECT * FROM messages
            WHERE id = $1 AND is_deleted = false
            "#,
            id
        )
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::not_found(format!("Message with ID {} not found", id)))
    }

    pub async fn get_messages_for_user(
        pool: &Pool<Postgres>,
        user_id: Uuid,
        other_user_id: Option<Uuid>,
        limit: i64,
        offset: i64,
    ) -> Result<Vec<Self>> {
        #[cfg(feature = "mock")]
        {
            let mut messages = Vec::new();
            for i in 0..5 {
                let message = Message {
                    id: Uuid::new_v4().to_string(),
                    sender_id: if i % 2 == 0 { user_id.to_string() } else { other_user_id.unwrap_or(Uuid::new_v4()).to_string() },
                    recipient_id: if i % 2 == 0 { other_user_id.unwrap_or(Uuid::new_v4()).to_string() } else { user_id.to_string() },
                    content: format!("Mock message #{}", i),
                    sent_at: chrono::Utc::now().to_rfc3339(),
                    received_at: Some(chrono::Utc::now().to_rfc3339()),
                    read_at: if i < 3 { Some(chrono::Utc::now().to_rfc3339()) } else { None },
                };
                messages.push(message);
            }
            
            return Ok(messages);
        }

        #[cfg(not(feature = "mock"))]
        {
            let messages = if let Some(other_id) = other_user_id {
                // Get conversation between two users
                sqlx::query_as!(
                    Message,
                    r#"
                    SELECT * FROM messages
                    WHERE ((sender_id = $1 AND recipient_id = $2) OR (sender_id = $2 AND recipient_id = $1))
                    AND is_deleted = false
                    ORDER BY sent_at DESC
                    LIMIT $3 OFFSET $4
                    "#,
                    user_id,
                    other_id,
                    limit,
                    offset
                )
                .fetch_all(pool)
                .await?
            } else {
                // Get all messages for user
                sqlx::query_as!(
                    Message,
                    r#"
                    SELECT * FROM messages
                    WHERE (sender_id = $1 OR recipient_id = $1)
                    AND is_deleted = false
                    ORDER BY sent_at DESC
                    LIMIT $2 OFFSET $3
                    "#,
                    user_id,
                    limit,
                    offset
                )
                .fetch_all(pool)
                .await?
            };

            Ok(messages)
        }
    }

    pub async fn mark_as_received(pool: &Pool<Postgres>, id: Uuid) -> Result<Self> {
        #[cfg(feature = "mock")]
        {
            let message = Message {
                id: id.to_string(),
                sender_id: Uuid::new_v4().to_string(),
                recipient_id: Uuid::new_v4().to_string(),
                content: "Mock message content".to_string(),
                sent_at: chrono::Utc::now().to_rfc3339(),
                received_at: Some(chrono::Utc::now().to_rfc3339()),
                read_at: None,
            };
            
            return Ok(message);
        }

        #[cfg(not(feature = "mock"))]
        sqlx::query_as!(
            Message,
            r#"
            UPDATE messages
            SET received_at = NOW()
            WHERE id = $1 AND received_at IS NULL
            RETURNING *
            "#,
            id
        )
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::not_found(format!("Message with ID {} not found or already marked as received", id)))
    }

    pub async fn mark_as_read(pool: &Pool<Postgres>, id: Uuid) -> Result<Self> {
        #[cfg(feature = "mock")]
        {
            let message = Message {
                id: id.to_string(),
                sender_id: Uuid::new_v4().to_string(),
                recipient_id: Uuid::new_v4().to_string(),
                content: "Mock message content".to_string(),
                sent_at: chrono::Utc::now().to_rfc3339(),
                received_at: Some(chrono::Utc::now().to_rfc3339()),
                read_at: Some(chrono::Utc::now().to_rfc3339()),
            };
            
            return Ok(message);
        }

        #[cfg(not(feature = "mock"))]
        sqlx::query_as!(
            Message,
            r#"
            UPDATE messages
            SET read_at = NOW()
            WHERE id = $1 AND read_at IS NULL
            RETURNING *
            "#,
            id
        )
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::not_found(format!("Message with ID {} not found or already marked as read", id)))
    }

    pub async fn mark_as_deleted(pool: &Pool<Postgres>, id: Uuid) -> Result<Self> {
        #[cfg(feature = "mock")]
        {
            let message = Message {
                id: id.to_string(),
                sender_id: Uuid::new_v4().to_string(),
                recipient_id: Uuid::new_v4().to_string(),
                content: "Mock message content (deleted)".to_string(),
                sent_at: chrono::Utc::now().to_rfc3339(),
                received_at: Some(chrono::Utc::now().to_rfc3339()),
                read_at: Some(chrono::Utc::now().to_rfc3339()),
            };
            
            return Ok(message);
        }

        #[cfg(not(feature = "mock"))]
        sqlx::query_as!(
            Message,
            r#"
            UPDATE messages
            SET is_deleted = true
            WHERE id = $1
            RETURNING *
            "#,
            id
        )
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::not_found(format!("Message with ID {} not found", id)))
    }

    pub fn to_response(&self) -> MessageResponse {
        MessageResponse {
            id: self.id.clone(),
            sender_id: self.sender_id.clone(),
            recipient_id: self.recipient_id.clone(),
            content: self.content.clone(),
            sent_at: self.sent_at.clone(),
            received_at: self.received_at.clone(),
            read_at: self.read_at.clone(),
        }
    }
}