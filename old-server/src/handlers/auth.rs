use axum::{
    extract::{Json, State},
    http::StatusCode,
    response::IntoResponse,
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use serde::{Deserialize, Serialize};
use sqlx::types::uuid::Uuid;
use validator::Validate;
use log::info;
use std::collections::HashMap;

use crate::{
    error::{AppError, Result},
    models::{
        auth::{
            AuthService, LoginRequest, AuthResponse, TokenRefreshRequest
        },
        user::{CreateUserRequest, User, UserProfile},
    },
    AppState,
};

pub async fn register(
    State(state): State<AppState>,
    Json(req): Json<CreateUserRequest>,
) -> Result<impl IntoResponse> {
    // Validate request
    req.validate()?;

    // Check if username exists
    if let Some(db) = &state.db {
        if let Ok(_) = User::find_by_username(db, &req.username).await {
            return Err(AppError::bad_request("Username already exists"));
        }

        // Check if email exists
        if let Ok(_) = User::find_by_email(db, &req.email).await {
            return Err(AppError::bad_request("Email already exists"));
        }

        // Hash password
        let password_hash = AuthService::hash_password(&req.password)?;

        // Create user
        let user = User::create(db, &req, &password_hash).await?;
        info!("User created: {}", user.id);

        // Generate JWT token
        let token = AuthService::create_token(&user.id, &user.username)?;

        // Return token
        let auth_response = AuthResponse {
            token,
            user: user.to_profile(),
            token_type: "Bearer".to_string(),
            expires_in: 86400, // 24 hours in seconds
        };

        Ok((StatusCode::CREATED, Json(auth_response)))
    } else {
        Err(AppError::internal("Database connection not available"))
    }
}

pub async fn login(
    State(state): State<AppState>,
    Json(payload): Json<LoginRequest>,
) -> Result<(StatusCode, Json<AuthResponse>)> {
    // Validate input
    payload.validate()?;
    
    // Find user by username
    if let Some(db) = &state.db {
        let user = User::find_by_username(&state.db, &payload.username).await?;
        
        // Verify password
        if !AuthService::verify_password(&payload.password, &user.password_hash)? {
            return Err(AppError::auth("Invalid username or password"));
        }
        
        // Generate JWT token
        let token = AuthService::create_token(&user.id, &user.username)?;
        
        // Generate refresh token
        let refresh_token = uuid::Uuid::new_v4().to_string();
        
        // Create response
        let auth_response = AuthResponse {
            token,
            refresh_token,
            token_type: "Bearer".to_string(),
            expires_in: 86400, // 24 hours
        };
        
        Ok((StatusCode::OK, Json(auth_response)))
    } else {
        Err(AppError::internal("Database not available"))
    }
}

pub async fn refresh_token(
    State(state): State<AppState>,
    Json(req): Json<TokenRefreshRequest>,
) -> Result<(StatusCode, Json<AuthResponse>)> {
    // Validate the refresh token
    if let Some(db) = &state.db {
        let token_data = AuthService::validate_token(req.refresh_token.as_str())?;
        
        // Get the user
        let user = User::find_by_id(&state.db, &token_data.sub).await?;
        
        // Generate a new access token
        let token = AuthService::create_token(&user.id, &user.username)?;
        
        // Return new token
        let auth_response = AuthResponse {
            token,
            refresh_token: req.refresh_token,
            token_type: "Bearer".to_string(),
            expires_in: 86400, // 24 hours
        };
        
        Ok((StatusCode::OK, Json(auth_response)))
    } else {
        Err(AppError::internal("Database not available"))
    }
}

pub async fn verify_token(
    TypedHeader(auth): TypedHeader<Authorization<Bearer>>,
    State(state): State<AppState>,
) -> Result<Json<HashMap<String, bool>>> {
    // Just validate the token, return true if valid
    let claims = AuthService::validate_token(auth.token())?;
    
    let mut response = HashMap::new();
    response.insert("valid".to_string(), true);
    
    Ok(Json(response))
}

pub async fn logout(
    TypedHeader(auth): TypedHeader<Authorization<Bearer>>,
    State(state): State<AppState>,
) -> Result<StatusCode> {
    // Validate token
    let _claims = AuthService::validate_token(auth.token())?;
    
    // In a real implementation, you would invalidate the token in a blacklist
    
    Ok(StatusCode::OK)
}

pub async fn validate_token(
    State(state): State<AppState>,
    TypedHeader(auth): TypedHeader<Authorization<Bearer>>,
) -> Result<impl IntoResponse> {
    // Just validate the token
    let _claims = AuthService::validate_token(&state.config, auth.token())?;

    Ok(StatusCode::NO_CONTENT)
} 