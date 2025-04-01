use std::convert::Infallible;

use axum::{
    async_trait,
    extract::{FromRef, FromRequestParts, State},
    http::{request::Parts, StatusCode},
    response::{IntoResponse, Response},
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use jsonwebtoken::{decode, DecodingKey, Validation};
use serde::{Deserialize, Serialize};

// ... existing code ... 