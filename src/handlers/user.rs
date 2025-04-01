use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use uuid::Uuid;
use validator::Validate;

// ... existing code ... 