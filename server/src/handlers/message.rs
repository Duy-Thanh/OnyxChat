use axum::{
    extract::{Json, Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
};
use serde::Deserialize;
use uuid::Uuid;

use crate::{
    error::{AppError, Result},
    middleware::auth::CurrentUser,
    models::{
        message::{Message, SendMessageRequest},
    },
    AppState,
};

#[derive(Debug, Deserialize)]
pub struct MessageQuery {
    #[serde(default = "default_limit")]
    pub limit: i64,
    #[serde(default)]
    pub offset: i64,
}

fn default_limit() -> i64 {
    50
}

pub async fn send_message(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Json(req): Json<SendMessageRequest>,
) -> Result<impl IntoResponse> {
    // Create the message
    let message = Message::create_simplified(
        &state.db,
        current_user.id,
        req.recipient_id.parse()?,
        &req.content,
    ).await?;

    Ok((StatusCode::CREATED, Json(message)))
}

pub async fn get_messages(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Query(query): Query<MessageQuery>,
) -> Result<impl IntoResponse> {
    // Get messages for user
    let messages = Message::get_messages_for_user(
        &state.db,
        current_user.id.parse()?,
        None,
        query.limit,
        query.offset,
    )
    .await?;

    let responses = messages.into_iter().map(|m| m.to_response()).collect::<Vec<_>>();

    Ok(Json(responses))
}

pub async fn get_messages_with_user(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Path(other_user_id): Path<Uuid>,
    Query(query): Query<MessageQuery>,
) -> Result<impl IntoResponse> {
    // Get messages between users
    let messages = Message::get_messages_for_user(
        &state.db,
        current_user.id.parse()?,
        Some(other_user_id),
        query.limit,
        query.offset,
    )
    .await?;

    let responses = messages.into_iter().map(|m| m.to_response()).collect::<Vec<_>>();

    Ok(Json(responses))
}

pub async fn get_message(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Path(message_id): Path<Uuid>,
) -> Result<impl IntoResponse> {
    // Get specific message
    let message = Message::find_by_id(&state.db, message_id).await?;

    // Ensure user is either sender or recipient
    if message.sender_id != current_user.id && message.recipient_id != current_user.id {
        return Err(crate::error::AppError::forbidden(
            "You don't have access to this message",
        ));
    }

    // If user is recipient and message not marked as read, mark it
    if message.recipient_id == current_user.id && message.read_at.is_none() {
        Message::mark_as_read(&state.db, message_id).await?;
    }

    // If user is recipient and message not marked as received, mark it
    if message.recipient_id == current_user.id && message.received_at.is_none() {
        Message::mark_as_received(&state.db, message_id).await?;
    }

    let response = message.to_response();

    Ok(Json(response))
}

pub async fn mark_message_received(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Path(message_id): Path<Uuid>,
) -> Result<impl IntoResponse> {
    // Get message
    let message = Message::find_by_id(&state.db, message_id).await?;

    // Ensure user is recipient
    if message.recipient_id != current_user.id {
        return Err(crate::error::AppError::forbidden(
            "You can only mark messages sent to you as received",
        ));
    }

    // Mark as received
    Message::mark_as_received(&state.db, message_id).await?;

    Ok(StatusCode::NO_CONTENT)
}

pub async fn mark_message_read(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Path(message_id): Path<Uuid>,
) -> Result<impl IntoResponse> {
    // Get message
    let message = Message::find_by_id(&state.db, message_id).await?;

    // Ensure user is recipient
    if message.recipient_id != current_user.id {
        return Err(crate::error::AppError::forbidden(
            "You can only mark messages sent to you as read",
        ));
    }

    // Mark as read
    Message::mark_as_read(&state.db, message_id).await?;

    Ok(StatusCode::NO_CONTENT)
}

pub async fn delete_message(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Path(message_id): Path<Uuid>,
) -> Result<impl IntoResponse> {
    // Get message
    let message = Message::find_by_id(&state.db, message_id).await?;

    // Ensure user is either sender or recipient
    if message.sender_id != current_user.id && message.recipient_id != current_user.id {
        return Err(crate::error::AppError::forbidden(
            "You can only delete messages you sent or received",
        ));
    }

    // Mark as deleted
    Message::mark_as_deleted(&state.db, message_id).await?;

    Ok(StatusCode::NO_CONTENT)
}