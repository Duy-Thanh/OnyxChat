use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
};
use axum_extra::{
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use uuid::Uuid;

use crate::{
    error::{AppError, Result},
    middleware::auth::CurrentUser,
    models::{
        auth::AuthService,
        user::{UpdateUserRequest, User},
        AppState,
    },
};

pub async fn get_user_profile(
    State(state): State<AppState>,
    Path(user_id): Path<Uuid>,
) -> Result<impl IntoResponse> {
    let user = User::find_by_id(&state.db, user_id).await?;
    let profile = user.to_profile();

    Ok(Json(profile))
}

pub async fn get_user_by_username(
    State(state): State<AppState>,
    Path(username): Path<String>,
) -> Result<impl IntoResponse> {
    let user = User::find_by_username(&state.db, &username).await?;
    let profile = user.to_profile();

    Ok(Json(profile))
}

pub async fn get_current_user(
    State(state): State<AppState>,
    current_user: CurrentUser,
) -> Result<impl IntoResponse> {
    let user = User::find_by_id(&state.db, current_user.user_id).await?;
    
    // Update last active
    User::update_last_active(&state.db, user.id).await?;
    
    Ok(Json(user))
}

pub async fn update_user(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Json(req): Json<UpdateUserRequest>,
) -> Result<impl IntoResponse> {
    // Validate request
    req.validate()?;

    // Get current user
    let mut user = User::find_by_id(&state.db, current_user.user_id).await?;

    // Check if password is being updated
    let password_hash = if let Some(ref new_password) = req.password {
        Some(AuthService::hash_password(new_password)?)
    } else {
        None
    };

    // Update user
    user = User::update(&state.db, user.id, &req, password_hash.as_deref()).await?;

    Ok(Json(user))
}

pub async fn delete_user(
    State(state): State<AppState>,
    current_user: CurrentUser,
    TypedHeader(auth): TypedHeader<Authorization<Bearer>>,
) -> Result<impl IntoResponse> {
    // Verify token
    let claims = AuthService::validate_token(&state.config, auth.token())?;
    
    // Ensure user ID in token matches current user
    if claims.sub != current_user.user_id.to_string() {
        return Err(AppError::unauthorized("Token does not match user"));
    }

    // First revoke all refresh tokens
    AuthService::revoke_all_user_tokens(&state.db, current_user.user_id).await?;

    // TODO: Implement soft delete or anonymization instead of hard delete
    // For now, we'll just return a not implemented error
    Err(AppError::not_implemented("Account deletion is not yet implemented"))
} 