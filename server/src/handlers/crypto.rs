use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
};
use uuid::Uuid;
use serde::Deserialize;

use crate::{
    error::Result,
    middleware::auth::CurrentUser,
    models::{
        crypto::{CryptoStore, OneTimePreKey, PreKeyBundle, UploadPreKeyBundleRequest, UserKey},
        AppState,
    },
};

#[derive(Debug, Deserialize)]
pub struct UpdateCryptoStoreRequest {
    pub identity_key: Option<String>,
    pub signed_prekey: Option<String>,
    pub signed_prekey_signature: Option<String>,
    pub one_time_prekeys: Option<Vec<crate::models::crypto::OneTimePreKeyRequest>>,
}

pub async fn upload_prekey_bundle(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Json(req): Json<UploadPreKeyBundleRequest>,
) -> Result<impl IntoResponse> {
    // Create or update user key
    let _user_key = UserKey::create_or_update(&state.db, current_user.id.parse()?, &req).await?;

    // Upload one-time prekeys
    let _prekeys =
        OneTimePreKey::create_batch(&state.db, current_user.id.parse()?, &req.one_time_prekeys).await?;

    Ok(StatusCode::CREATED)
}

pub async fn get_prekey_bundle(
    State(state): State<AppState>,
    _current_user: CurrentUser,
    Path(user_id): Path<Uuid>,
) -> Result<impl IntoResponse> {
    // Get user key
    let user_key = UserKey::find_by_user_id(&state.db, user_id).await?;

    // Get an unused one-time prekey
    let one_time_prekey = OneTimePreKey::get_unused_for_user(&state.db, user_id).await?;

    let prekey_bundle = PreKeyBundle {
        user_id,
        identity_key: user_key.identity_key,
        signed_prekey: user_key.signed_prekey,
        signed_prekey_signature: user_key.signed_prekey_signature,
        prekey_id: user_key.prekey_id,
        one_time_prekey: one_time_prekey.map(|pk| {
            crate::models::crypto::OneTimePreKeyResponse {
                prekey_id: pk.prekey_id,
                prekey: pk.prekey,
            }
        }),
    };

    Ok(Json(prekey_bundle))
}

pub async fn update_crypto_store(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Json(req): Json<UpdateCryptoStoreRequest>,
) -> Result<impl IntoResponse> {
    // Update the crypto store
    CryptoStore::update(&state.db, current_user.id.parse()?, &req).await?;

    // Update one-time prekeys if provided
    if let Some(prekeys) = &req.one_time_prekeys {
        if !prekeys.is_empty() {
            OneTimePreKey::create_batch_from_option(&state.db, current_user.id.parse()?, &req.one_time_prekeys).await?;
        }
    }

    Ok(StatusCode::NO_CONTENT)
}