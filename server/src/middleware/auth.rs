use std::convert::Infallible;

use axum::{
    async_trait,
    extract::{FromRef, FromRequestParts, State},
    http::{request::Parts, StatusCode},
};
use axum_extra::{
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use uuid::Uuid;

use crate::{
    error::AppError,
    models::{auth::AuthService, AppState},
};

#[derive(Debug, Clone)]
pub struct CurrentUser {
    pub user_id: Uuid,
    pub username: String,
}

#[async_trait]
impl<S> FromRequestParts<S> for CurrentUser
where
    AppState: FromRef<S>,
    S: Send + Sync,
{
    type Rejection = AppError;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        // Extract the token from the Authorization header
        let TypedHeader(Authorization(bearer)) =
            TypedHeader::<Authorization<Bearer>>::from_request_parts(parts, state)
                .await
                .map_err(|_| AppError::auth("Missing authorization header"))?;

        // Extract the AppState
        let app_state = AppState::from_ref(state);

        // Validate the token
        let claims = AuthService::validate_token(&app_state.config, bearer.token())?;

        // Extract user_id from the "sub" field
        let user_id = claims
            .sub
            .parse::<Uuid>()
            .map_err(|_| AppError::auth("Invalid user ID in token"))?;

        Ok(CurrentUser {
            user_id,
            username: claims.username,
        })
    }
}

// Optional version that doesn't require authentication
#[derive(Debug, Clone)]
pub struct OptionalUser {
    pub user: Option<CurrentUser>,
}

#[async_trait]
impl<S> FromRequestParts<S> for OptionalUser
where
    AppState: FromRef<S>,
    S: Send + Sync,
{
    type Rejection = Infallible;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        // Try to extract the user, but don't fail if not possible
        let user = CurrentUser::from_request_parts(parts, state).await.ok();
        Ok(OptionalUser { user })
    }
} 