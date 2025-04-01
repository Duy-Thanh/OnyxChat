use axum::{
    extract::{Json, State},
    http::StatusCode,
    response::IntoResponse,
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use validator::Validate;

// ... existing code ... 