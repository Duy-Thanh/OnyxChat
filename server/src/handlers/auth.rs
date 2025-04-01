use axum::{
    extract::{Json, State},
    http::StatusCode,
    response::IntoResponse,
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use validator::Validate;

use crate::{
    error::{AppError, Result},
    models::{
        auth::{AuthResponse, AuthService, LoginRequest, TokenRefreshRequest},
        user::{CreateUserRequest, User},
        AppState,
    },
};

pub async fn register(
    State(state): State<AppState>,
    Json(req): Json<CreateUserRequest>,
) -> Result<impl IntoResponse> {
    // Validate request
    req.validate()?;

    // Hash password
    let password_hash = AuthService::hash_password(&req.password)?;

    // Create user
    let user = User::create(&state.db, &req, &password_hash).await?;

    // Generate JWT token
    let token = AuthService::create_token(&user.id.to_string(), &user.username)?;

    // Create refresh token
    let refresh_token = AuthService::create_refresh_token(&state.db, uuid::Uuid::parse_str(&user.id)?).await?;

    let auth_response = AuthResponse {
        token,
        refresh_token,
        expires_in: state.config.jwt_expiration as u64,
        token_type: "Bearer".to_string(),
    };

    Ok((StatusCode::CREATED, Json(auth_response)))
}

pub async fn login(
    State(state): State<AppState>,
    Json(req): Json<LoginRequest>,
) -> Result<impl IntoResponse> {
    // Validate request
    req.validate()?;

    // Find user by username
    let user = User::find_by_username(&state.db, &req.username).await?;

    // Verify password
    if !AuthService::verify_password(&req.password, &user.password_hash)? {
        return Err(AppError::auth("Invalid credentials"));
    }

    // Update last active
    User::update_last_active(&state.db, user.id.clone()).await?;

    // Generate JWT token
    let token = AuthService::create_token(&user.id.to_string(), &user.username)?;

    // Create refresh token
    let refresh_token = AuthService::create_refresh_token(&state.db, uuid::Uuid::parse_str(&user.id)?).await?;

    let auth_response = AuthResponse {
        token,
        refresh_token,
        expires_in: state.config.jwt_expiration as u64,
        token_type: "Bearer".to_string(),
    };

    Ok((StatusCode::OK, Json(auth_response)))
}

pub async fn refresh_token(
    State(state): State<AppState>,
    Json(req): Json<TokenRefreshRequest>,
) -> Result<impl IntoResponse> {
    // Validate refresh token
    let token_data = AuthService::validate_refresh_token(&state.db, &req.refresh_token).await?;

    // Find user
    let user = User::find_by_id(&state.db, token_data.user_id).await?;

    // Generate new JWT token
    let token = AuthService::create_token(&user.id.to_string(), &user.username)?;

    // Create new refresh token
    let refresh_token = AuthService::create_refresh_token(&state.db, uuid::Uuid::parse_str(&user.id)?).await?;

    // Revoke old refresh token
    AuthService::revoke_refresh_token(&state.db, &req.refresh_token).await?;

    let auth_response = AuthResponse {
        token,
        refresh_token,
        expires_in: state.config.jwt_expiration as u64,
        token_type: "Bearer".to_string(),
    };

    Ok((StatusCode::OK, Json(auth_response)))
}

pub async fn logout(
    State(state): State<AppState>,
    TypedHeader(auth): TypedHeader<Authorization<Bearer>>,
) -> Result<impl IntoResponse> {
    // Validate token
    let claims = AuthService::validate_token(&state.config, auth.token())?;

    // Revoke all refresh tokens for user
    AuthService::revoke_all_user_tokens(&state.db, claims.sub.parse()?).await?;

    Ok(StatusCode::NO_CONTENT)
}

pub async fn validate_token(
    State(state): State<AppState>,
    TypedHeader(auth): TypedHeader<Authorization<Bearer>>,
) -> Result<impl IntoResponse> {
    // Just validate the token
    let _claims = AuthService::validate_token(&state.config, auth.token())?;

    Ok(StatusCode::NO_CONTENT)
} 