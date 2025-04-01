use serde::{Deserialize, Serialize};
use sqlx::{FromRow, Pool, Postgres};
use uuid::Uuid;
use chrono::{DateTime, Utc};
use validator::Validate;

use crate::error::{AppError, Result};

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct Message {
    pub id: Uuid,
    pub sender_id: Uuid,
    pub recipient_id: Uuid,
    pub encrypted_content: String,
    pub iv: String,
    pub sent_at: DateTime<Utc>,
    pub received_at: Option<DateTime<Utc>>,
    pub read_at: Option<DateTime<Utc>>,
    pub is_deleted: bool,
}

#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct SendMessageRequest {
    pub recipient_id: Uuid,
    pub encrypted_content: String,
    pub iv: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct MessageResponse {
    pub id: Uuid,
    pub sender_id: Uuid,
    pub recipient_id: Uuid,
    pub encrypted_content: String,
    pub iv: String,
    pub sent_at: DateTime<Utc>,
    pub received_at: Option<DateTime<Utc>>,
    pub read_at: Option<DateTime<Utc>>,
}

impl Message {
    pub async fn create(
        pool: &Pool<Postgres>,
        sender_id: Uuid,
        recipient_id: Uuid,
        encrypted_content: &str,
        iv: &str,
    ) -> Result<Self> {
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

    pub async fn find_by_id(pool: &Pool<Postgres>, id: Uuid) -> Result<Self> {
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

    pub async fn mark_as_received(pool: &Pool<Postgres>, id: Uuid) -> Result<Self> {
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
            id: self.id,
            sender_id: self.sender_id,
            recipient_id: self.recipient_id,
            encrypted_content: self.encrypted_content.clone(),
            iv: self.iv.clone(),
            sent_at: self.sent_at,
            received_at: self.received_at,
            read_at: self.read_at,
        }
    }

    // Mark a message as read
    pub async fn mark_as_read(db: &Pool<Postgres>, message_id: &str) -> Result<Message, sqlx::Error> {
        let now = Utc::now();
        
        // Update message in database
        let message = sqlx::query_as!(
            Message,
            r#"
            UPDATE messages
            SET read_at = $1
            WHERE id = $2 AND is_deleted = false
            RETURNING id, sender_id, recipient_id, encrypted_content, iv, sent_at, received_at, read_at, is_deleted
            "#,
            now,
            message_id
        )
        .fetch_one(db)
        .await?;
        
        Ok(message)
    }
    
    // Mark a message as received
    pub async fn mark_as_received(db: &Pool<Postgres>, message_id: &str) -> Result<Message, sqlx::Error> {
        let now = Utc::now();
        
        // Update message in database
        let message = sqlx::query_as!(
            Message,
            r#"
            UPDATE messages
            SET received_at = $1
            WHERE id = $2 AND is_deleted = false
            RETURNING id, sender_id, recipient_id, encrypted_content, iv, sent_at, received_at, read_at, is_deleted
            "#,
            now,
            message_id
        )
        .fetch_one(db)
        .await?;
        
        Ok(message)
    }
}