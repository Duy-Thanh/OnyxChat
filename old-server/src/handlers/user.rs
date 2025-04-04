use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use uuid::Uuid;
use validator::Validate;
use serde_json::{json, Value};
use sqlx::types::time::OffsetDateTime;

use crate::{
    error::{AppError, Result},
    middleware::auth::CurrentUser,
    models::{
        auth::AuthService,
        user::{UpdateUserRequest, User, CreateUserRequest},
    },
    AppState,
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

pub async fn get_user_by_id(
    State(state): State<AppState>,
    Path(user_id): Path<Uuid>,
) -> Result<Json<Value>> {
    let user = User::find_by_id(&state.db, &user_id.to_string()).await?;
    
    let profile = json!({
        "id": user.id,
        "username": user.username,
        "display_name": user.display_name,
        "created_at": user.created_at,
    });
    
    Ok(Json(profile))
}

pub async fn update_user(
    State(state): State<AppState>,
    Path(user_id): Path<Uuid>,
    Json(req): Json<UpdateUserRequest>,
    current_user: CurrentUser,
) -> Result<Json<Value>> {
    if current_user.id != user_id.to_string() {
        return Err(AppError::forbidden("Cannot update another user's profile"));
    }
    
    let user = User::find_by_id(&state.db, &user_id.to_string()).await?;
    
    let updated_user = User::update(&state.db, &user.id, &req, None).await?;
    
    let profile = json!({
        "id": updated_user.id,
        "username": updated_user.username,
        "display_name": updated_user.display_name,
        "created_at": updated_user.created_at,
        "updated_at": updated_user.updated_at,
    });
    
    Ok(Json(profile))
}

pub async fn delete_user(
    State(state): State<AppState>,
    Path(user_id): Path<Uuid>,
    current_user: CurrentUser,
) -> Result<StatusCode> {
    if current_user.id != user_id.to_string() {
        return Err(AppError::forbidden("Cannot delete another user's account"));
    }
    
    let user = User::find_by_id(&state.db, &user_id.to_string()).await?;
    
    Ok(StatusCode::NO_CONTENT)
}

pub async fn change_password(
    TypedHeader(auth): TypedHeader<Authorization<Bearer>>,
    State(state): State<AppState>,
    Json(req): Json<Value>,
) -> Result<StatusCode> {
    let claims = AuthService::validate_token(auth.token())?;
    
    Ok(StatusCode::OK)
} 