use serde::{Deserialize, Serialize};
use sqlx::{FromRow, Pool, Postgres};
use uuid::Uuid;
use chrono::{DateTime, Utc};
use validator::Validate;
use rand::Rng;

use crate::error::{AppError, Result};
use crate::handlers::crypto::UpdateCryptoStoreRequest;

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct UserKey {
    pub id: Uuid,
    pub user_id: Uuid,
    pub identity_key: String,
    pub signed_prekey: String,
    pub signed_prekey_signature: String,
    pub prekey_id: i32,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct OneTimePreKey {
    pub id: Uuid,
    pub user_id: Uuid,
    pub prekey_id: i32,
    pub prekey: String,
    pub used: bool,
    pub created_at: DateTime<Utc>,
    pub used_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct Session {
    pub id: Uuid,
    pub user_id: Uuid,
    pub other_user_id: Uuid,
    pub session_data: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Serialize, Deserialize, Validate)]
pub struct UploadPreKeyBundleRequest {
    pub identity_key: String,
    pub signed_prekey: String,
    pub signed_prekey_signature: String,
    pub prekey_id: i32,
    pub one_time_prekeys: Vec<OneTimePreKeyUpload>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct OneTimePreKeyUpload {
    pub prekey_id: i32,
    pub prekey: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct PreKeyBundle {
    pub user_id: Uuid,
    pub identity_key: String,
    pub signed_prekey: String,
    pub signed_prekey_signature: String,
    pub prekey_id: i32,
    pub one_time_prekey: Option<OneTimePreKeyResponse>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct OneTimePreKeyResponse {
    pub prekey_id: i32,
    pub prekey: String,
}

pub struct CryptoService;

impl CryptoService {
    // In a real application, these would implement actual encryption/decryption logic
    // But for this demo, we just return the input
    
    pub fn encrypt_message(content: &str, _key: &str) -> Result<(String, String)> {
        // Generate a random IV for the mock encryption
        let iv = rand::thread_rng()
            .sample_iter(&rand::distributions::Alphanumeric)
            .take(16)
            .map(char::from)
            .collect::<String>();
            
        // In a real app, we'd encrypt the content with the key and IV
        let encrypted_content = content.to_string();
        
        Ok((encrypted_content, iv))
    }
    
    pub fn decrypt_message(encrypted_content: &str, _iv: &str, _key: &str) -> Result<String> {
        // In a real app, we'd decrypt the content using the key and IV
        let decrypted_content = encrypted_content.to_string();
        
        Ok(decrypted_content)
    }
    
    pub fn generate_key_pair() -> Result<(String, String)> {
        // In a real app, this would generate a public/private key pair
        // For this demo, we just generate random strings
        
        let private_key = rand::thread_rng()
            .sample_iter(&rand::distributions::Alphanumeric)
            .take(32)
            .map(char::from)
            .collect::<String>();
            
        let public_key = rand::thread_rng()
            .sample_iter(&rand::distributions::Alphanumeric)
            .take(32)
            .map(char::from)
            .collect::<String>();
            
        Ok((public_key, private_key))
    }
}

impl UserKey {
    pub async fn create_or_update(
        pool: &Pool<Postgres>,
        user_id: Uuid,
        request: &UploadPreKeyBundleRequest,
    ) -> Result<Self> {
        #[cfg(feature = "mock")]
        {
            let user_key = UserKey {
                id: Uuid::new_v4(),
                user_id,
                identity_key: request.identity_key.clone(),
                signed_prekey: request.signed_prekey.clone(),
                signed_prekey_signature: request.signed_prekey_signature.clone(),
                prekey_id: request.prekey_id,
                created_at: Utc::now(),
            };
            
            return Ok(user_key);
        }

        #[cfg(not(feature = "mock"))]
        {
            // Delete existing key if exists
            sqlx::query!(
                r#"DELETE FROM user_keys WHERE user_id = $1"#,
                user_id
            )
            .execute(pool)
            .await?;

            // Insert new key
            let user_key = sqlx::query_as!(
                UserKey,
                r#"
                INSERT INTO user_keys (user_id, identity_key, signed_prekey, signed_prekey_signature, prekey_id)
                VALUES ($1, $2, $3, $4, $5)
                RETURNING *
                "#,
                user_id,
                request.identity_key,
                request.signed_prekey,
                request.signed_prekey_signature,
                request.prekey_id
            )
            .fetch_one(pool)
            .await
            .map_err(|e| AppError::internal(format!("Failed to create user key: {}", e)))?;

            Ok(user_key)
        }
    }

    pub async fn find_by_user_id(pool: &Pool<Postgres>, user_id: Uuid) -> Result<Self> {
        #[cfg(feature = "mock")]
        {
            let user_key = UserKey {
                id: Uuid::new_v4(),
                user_id,
                identity_key: "mock_identity_key".to_string(),
                signed_prekey: "mock_signed_prekey".to_string(),
                signed_prekey_signature: "mock_signature".to_string(),
                prekey_id: 1,
                created_at: Utc::now(),
            };
            
            return Ok(user_key);
        }

        #[cfg(not(feature = "mock"))]
        sqlx::query_as!(
            UserKey,
            r#"
            SELECT * FROM user_keys
            WHERE user_id = $1
            "#,
            user_id
        )
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::not_found(format!("Key bundle for user {} not found", user_id)))
    }
}

impl OneTimePreKey {
    pub async fn create_batch(
        pool: &Pool<Postgres>,
        user_id: Uuid,
        prekeys: &[OneTimePreKeyUpload],
    ) -> Result<Vec<Self>> {
        #[cfg(feature = "mock")]
        {
            let mut created_prekeys = Vec::new();
            for prekey in prekeys {
                let new_prekey = OneTimePreKey {
                    id: Uuid::new_v4(),
                    user_id,
                    prekey_id: prekey.prekey_id,
                    prekey: prekey.prekey.clone(),
                    used: false,
                    created_at: Utc::now(),
                    used_at: None,
                };
                created_prekeys.push(new_prekey);
            }
            return Ok(created_prekeys);
        }

        #[cfg(not(feature = "mock"))]
        {
            // Create a transaction
            let mut tx = pool.begin().await?;

            let mut created_prekeys = Vec::new();

            for prekey in prekeys {
                // Check if prekey_id already exists for this user
                let existing = sqlx::query!(
                    r#"
                    SELECT id FROM one_time_prekeys
                    WHERE user_id = $1 AND prekey_id = $2
                    "#,
                    user_id,
                    prekey.prekey_id
                )
                .fetch_optional(&mut *tx)
                .await?;

                if existing.is_none() {
                    // Insert new prekey
                    let new_prekey = sqlx::query_as!(
                        OneTimePreKey,
                        r#"
                        INSERT INTO one_time_prekeys (user_id, prekey_id, prekey)
                        VALUES ($1, $2, $3)
                        RETURNING *
                        "#,
                        user_id,
                        prekey.prekey_id,
                        prekey.prekey
                    )
                    .fetch_one(&mut *tx)
                    .await
                    .map_err(|e| AppError::internal(format!("Failed to create prekey: {}", e)))?;

                    created_prekeys.push(new_prekey);
                }
            }

            // Commit transaction
            tx.commit().await?;

            Ok(created_prekeys)
        }
    }

    pub async fn get_unused_for_user(pool: &Pool<Postgres>, user_id: Uuid) -> Result<Option<Self>> {
        #[cfg(feature = "mock")]
        {
            let prekey = OneTimePreKey {
                id: Uuid::new_v4(),
                user_id,
                prekey_id: 1,
                prekey: "mock_prekey".to_string(),
                used: false,
                created_at: Utc::now(),
                used_at: None,
            };
            
            return Ok(Some(prekey));
        }

        #[cfg(not(feature = "mock"))]
        {
            // Start a transaction
            let mut tx = pool.begin().await?;

            // Get one unused prekey
            let prekey = sqlx::query_as!(
                OneTimePreKey,
                r#"
                SELECT * FROM one_time_prekeys
                WHERE user_id = $1 AND used = false
                LIMIT 1
                "#,
                user_id
            )
            .fetch_optional(&mut *tx)
            .await?;

            if let Some(prekey) = prekey.as_ref() {
                // Mark the prekey as used
                sqlx::query!(
                    r#"
                    UPDATE one_time_prekeys
                    SET used = true, used_at = NOW()
                    WHERE id = $1
                    "#,
                    prekey.id
                )
                .execute(&mut *tx)
                .await?;
            }

            // Commit transaction
            tx.commit().await?;

            Ok(prekey)
        }
    }

    pub async fn create_batch_from_option(
        _pool: &sqlx::Pool<sqlx::Postgres>,
        _user_id: uuid::Uuid,
        _prekeys: &Option<Vec<OneTimePreKeyRequest>>,
    ) -> crate::error::Result<Vec<Self>> {
        // This is a mock implementation
        // For development, return an empty vector
        Ok(Vec::new())
    }
}

impl Session {
    pub async fn create_or_update(
        pool: &Pool<Postgres>,
        user_id: Uuid,
        other_user_id: Uuid,
        session_data: &str,
    ) -> Result<Self> {
        #[cfg(feature = "mock")]
        {
            let session = Session {
                id: Uuid::new_v4(),
                user_id,
                other_user_id,
                session_data: session_data.to_string(),
                created_at: Utc::now(),
                updated_at: Utc::now(),
            };
            
            return Ok(session);
        }

        #[cfg(not(feature = "mock"))]
        {
            // Check if session already exists
            let existing = sqlx::query!(
                r#"
                SELECT id FROM sessions
                WHERE user_id = $1 AND other_user_id = $2
                "#,
                user_id,
                other_user_id
            )
            .fetch_optional(pool)
            .await?;

            let session = if let Some(existing) = existing {
                // Update existing session
                sqlx::query_as!(
                    Session,
                    r#"
                    UPDATE sessions
                    SET session_data = $1, updated_at = NOW()
                    WHERE id = $2
                    RETURNING *
                    "#,
                    session_data,
                    existing.id
                )
                .fetch_one(pool)
                .await?
            } else {
                // Create new session
                sqlx::query_as!(
                    Session,
                    r#"
                    INSERT INTO sessions (user_id, other_user_id, session_data)
                    VALUES ($1, $2, $3)
                    RETURNING *
                    "#,
                    user_id,
                    other_user_id,
                    session_data
                )
                .fetch_one(pool)
                .await?
            };

            Ok(session)
        }
    }

    pub async fn find_by_user_pair(
        pool: &Pool<Postgres>,
        user_id: Uuid,
        other_user_id: Uuid,
    ) -> Result<Option<Self>> {
        #[cfg(feature = "mock")]
        {
            let session = Session {
                id: Uuid::new_v4(),
                user_id,
                other_user_id,
                session_data: "mock_session_data".to_string(),
                created_at: Utc::now(),
                updated_at: Utc::now(),
            };
            
            return Ok(Some(session));
        }

        #[cfg(not(feature = "mock"))]
        {
            let session = sqlx::query_as!(
                Session,
                r#"
                SELECT * FROM sessions
                WHERE user_id = $1 AND other_user_id = $2
                "#,
                user_id,
                other_user_id
            )
            .fetch_optional(pool)
            .await?;
            
            Ok(session)
        }
    }
}

pub struct CryptoStore;

impl CryptoStore {
    pub async fn update(
        _pool: &sqlx::Pool<sqlx::Postgres>,
        _user_id: uuid::Uuid,
        _request: &UpdateCryptoStoreRequest,
    ) -> crate::error::Result<()> {
        // This is a mock implementation for now
        // In a real implementation, this would update the crypto store in the database
        Ok(())
    }
}

// Define the OneTimePreKeyRequest struct for serialization/deserialization
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OneTimePreKeyRequest {
    pub prekey_id: i32,
    pub prekey: String,
} 