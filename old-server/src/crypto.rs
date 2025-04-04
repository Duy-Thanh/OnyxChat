use axum::{
    extract::{Path, State},
    Json,
    routing::{get, post},
    Router,
};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::{
    auth::CurrentUser,
    error::{AppError, Result},
    AppState,
};

// Crypto models

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserKey {
    pub id: String,
    pub user_id: String,
    pub public_key: String,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OneTimePrekey {
    pub id: String,
    pub user_id: String,
    pub key_id: String,
    pub public_key: String,
    pub created_at: String,
    pub used: bool,
    pub used_at: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateUserKeyRequest {
    pub public_key: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreatePrekeyRequest {
    pub key_id: String,
    pub public_key: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PrekeyBundle {
    pub user_id: String,
    pub identity_key: String,
    pub prekey: Option<PreKeyInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PreKeyInfo {
    pub key_id: String,
    pub public_key: String,
}

// Routes setup
pub fn crypto_routes() -> Router<AppState> {
    Router::new()
        .route("/keys", post(register_user_key))
        .route("/keys/:user_id", get(get_user_keys))
        .route("/prekeys", post(register_prekey))
        .route("/prekeys/bundle/:user_id", get(get_prekey_bundle))
}

// Register a user's identity key
async fn register_user_key(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Json(payload): Json<CreateUserKeyRequest>,
) -> Result<Json<UserKey>> {
    // Validate request
    if payload.public_key.is_empty() {
        return Err(AppError::BadRequest("Public key is required".to_string()));
    }

    // Create a new user key
    let key_id = Uuid::new_v4().to_string();
    let created_at = chrono::Utc::now().to_rfc3339();

    let user_key = UserKey {
        id: key_id.clone(),
        user_id: current_user.user_id.clone(),
        public_key: payload.public_key,
        created_at,
    };

    // Store the key
    state.user_keys.write().unwrap().insert(key_id, user_key.clone());

    Ok(Json(user_key))
}

// Get a user's identity key
async fn get_user_keys(
    State(state): State<AppState>,
    Path(user_id): Path<String>,
) -> Result<Json<Vec<UserKey>>> {
    // Get keys for user
    let keys = state.user_keys.read().unwrap();
    let user_keys: Vec<UserKey> = keys
        .values()
        .filter(|key| key.user_id == user_id)
        .cloned()
        .collect();

    if user_keys.is_empty() {
        return Err(AppError::NotFound(format!("No keys found for user {}", user_id)));
    }

    Ok(Json(user_keys))
}

// Register a one-time prekey
async fn register_prekey(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Json(payload): Json<CreatePrekeyRequest>,
) -> Result<Json<OneTimePrekey>> {
    // Validate request
    if payload.public_key.is_empty() || payload.key_id.is_empty() {
        return Err(AppError::BadRequest("Public key and key ID are required".to_string()));
    }

    // Create a new prekey
    let prekey_id = Uuid::new_v4().to_string();
    let created_at = chrono::Utc::now().to_rfc3339();

    let prekey = OneTimePrekey {
        id: prekey_id.clone(),
        user_id: current_user.user_id.clone(),
        key_id: payload.key_id,
        public_key: payload.public_key,
        created_at,
        used: false,
        used_at: None,
    };

    // Store the prekey
    state.prekeys.write().unwrap().insert(prekey_id, prekey.clone());

    Ok(Json(prekey))
}

// Get a prekey bundle for a user
async fn get_prekey_bundle(
    State(state): State<AppState>,
    Path(user_id): Path<String>,
) -> Result<Json<PrekeyBundle>> {
    // Get the user's identity key
    let keys = state.user_keys.read().unwrap();
    let identity_key = keys
        .values()
        .find(|key| key.user_id == user_id)
        .cloned()
        .ok_or_else(|| AppError::NotFound(format!("No identity key found for user {}", user_id)))?;

    // Find an unused prekey
    let mut prekeys = state.prekeys.write().unwrap();
    let prekey = prekeys
        .values_mut()
        .find(|pk| pk.user_id == user_id && !pk.used)
        .cloned();

    // Mark the prekey as used if found
    if let Some(pk) = prekey.clone() {
        if let Some(found_prekey) = prekeys.get_mut(&pk.id) {
            found_prekey.used = true;
            found_prekey.used_at = Some(chrono::Utc::now().to_rfc3339());
        }
    }

    // Create the prekey bundle
    let bundle = PrekeyBundle {
        user_id,
        identity_key: identity_key.public_key,
        prekey: prekey.map(|pk| PreKeyInfo {
            key_id: pk.key_id,
            public_key: pk.public_key,
        }),
    };

    Ok(Json(bundle))
} 