use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use uuid::Uuid;
use validator::Validate;

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

pub async fn get_user(
    State(state): State<AppState>,
    Path(user_id): Path<Uuid>,
) -> Result<impl IntoResponse> {
    let user = User::find_by_id(&state.db, user_id).await?;
    Ok(Json(user))
}

pub async fn get_current_user(
    State(state): State<AppState>,
    current_user: CurrentUser,
) -> Result<impl IntoResponse> {
    let user_id = Uuid::parse_str(&current_user.id)?;
    let user = User::find_by_id(&state.db, user_id).await?;
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
    let user_id = Uuid::parse_str(&current_user.id)?;
    let user = User::find_by_id(&state.db, user_id).await?;

    // Update user
    let updated_user = User::update(&state.db, user.id, &req, None).await?;

    Ok(Json(updated_user))
}

pub async fn delete_user(
    State(state): State<AppState>,
    current_user: CurrentUser,
    TypedHeader(auth): TypedHeader<Authorization<Bearer>>,
) -> Result<impl IntoResponse> {
    // Verify JWT claims
    let claims = AuthService::validate_token(&state.config, auth.token())?;

    // Ensure the token's subject matches the current user's ID
    if claims.sub != current_user.id {
        return Err(AppError::forbidden("You can only delete your own account"));
    }

    // Revoke all refresh tokens
    AuthService::revoke_all_user_tokens(&state.db, uuid::Uuid::parse_str(&current_user.id)?).await?;

    // Delete user (this might just mark the user as deleted in a real app)
    return Ok(StatusCode::NOT_IMPLEMENTED);
} 