use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
};
use uuid::Uuid;

use crate::{
    error::Result,
    middleware::auth::CurrentUser,
    models::{
        crypto::{OneTimePreKey, PreKeyBundle, UploadPreKeyBundleRequest, UserKey},
        AppState,
    },
};

pub async fn upload_prekey_bundle(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Json(req): Json<UploadPreKeyBundleRequest>,
) -> Result<impl IntoResponse> {
    // Create or update user key
    let _user_key = UserKey::create_or_update(&state.db, current_user.user_id, &req).await?;

    // Upload one-time prekeys
    let _prekeys =
        OneTimePreKey::create_batch(&state.db, current_user.user_id, &req.one_time_prekeys).await?;

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