use std::sync::Arc;
use std::net::SocketAddr;

use axum::{
    Router,
    routing::{get, post},
    http::{StatusCode, header},
    extract::State,
    response::IntoResponse,
    Json,
};
use log::{info, error};
use dotenv::dotenv;
use env_logger;
use tower_http::cors::{Any, CorsLayer};

mod routes;
mod models;
mod handlers;
mod middleware;
mod ws;

use crate::models::AppState;
use crate::middleware::auth::auth;
use crate::routes::{users, auth as auth_routes, messages};
use crate::ws::WebSocketManager;

// User model
#[derive(Debug, Clone, Serialize)]
struct User {
    id: String,
    username: String,
    email: String,
    #[serde(skip_serializing)]
    password_hash: String,
    created_at: String,
}

// Message model
#[derive(Debug, Clone, Serialize)]
struct Message {
    id: String,
    sender_id: String,
    recipient_id: String,
    content: String,
    sent_at: String,
    received_at: Option<String>,
    read_at: Option<String>,
}

// Request model for user registration
#[derive(Debug, Deserialize)]
struct CreateUserRequest {
    username: String,
    email: String,
    password: String,
}

// Request model for sending a message
#[derive(Debug, Deserialize)]
struct SendMessageRequest {
    recipient_id: String,
    content: String,
}

// Success response for user creation
#[derive(Debug, Serialize)]
struct UserCreatedResponse {
    id: String,
    username: String,
    email: String,
}

// Success response for message creation
#[derive(Debug, Serialize)]
struct MessageResponse {
    id: String,
    sender_id: String,
    recipient_id: String,
    content: String,
    sent_at: String,
    received_at: Option<String>,
    read_at: Option<String>,
}

#[tokio::main]
async fn main() {
    // Initialize environment
    dotenv().ok();
    env_logger::init();
    
    // Create WebSocket manager
    let ws_manager = Arc::new(WebSocketManager::new());
    
    // Create application state
    let app_state = AppState::new(None, ws_manager);
    
    // CORS configuration
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    // Create router
    let app = Router::new()
        // Health check endpoint
        .route("/api/health", get(health_check))
        
        // Merge user routes
        .merge(users::user_routes())
        
        // Merge auth routes
        .merge(auth_routes::auth_routes())
        
        // Merge message routes
        .merge(messages::message_routes())
        
        // Merge WebSocket routes
        .merge(ws::ws_routes())
        
        // Apply middleware
        .layer(cors)
        
        // Add app state
        .with_state(app_state);

    // Start server
    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    info!("Starting server on {}", addr);
    
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();
}

// Health check endpoint
async fn health_check() -> impl IntoResponse {
    (StatusCode::OK, "OK")
}

async fn hello_world() -> &'static str {
    "Hello, World from OnyxChat!"
}

async fn register_user(
    State(state): State<AppState>,
    Json(payload): Json<CreateUserRequest>,
) -> Result<(StatusCode, Json<UserCreatedResponse>)> {
    // Validate request
    if payload.username.is_empty() || payload.email.is_empty() || payload.password.is_empty() {
        return Err(AppError::BadRequest("Username, email, and password are required".to_string()));
    }

    // Check if username already exists
    {
        let users = state.users.read().unwrap();
        if users.values().any(|user| user.username == payload.username) {
            return Err(AppError::Conflict("Username already exists".to_string()));
        }
    }

    // Hash the password
    let password_hash = AuthService::hash_password(&payload.password)?;

    // Create a new user
    let user_id = Uuid::new_v4().to_string();
    let created_at = chrono::Utc::now().to_rfc3339();

    let user = User {
        id: user_id.clone(),
        username: payload.username.clone(),
        email: payload.email.clone(),
        password_hash,
        created_at,
    };

    // Store the user
    {
        let mut users = state.users.write().unwrap();
        users.insert(user_id.clone(), user.clone());
    }

    info!("User created: {}", user_id);

    // Return success
    Ok((
        StatusCode::CREATED,
        Json(UserCreatedResponse {
            id: user_id,
            username: user.username,
            email: user.email,
        }),
    ))
}

async fn login(
    State(state): State<AppState>,
    Json(payload): Json<LoginRequest>,
) -> Result<(StatusCode, Json<AuthResponse>)> {
    // Find user by username
    let user = {
        let users = state.users.read().unwrap();
        users
            .values()
            .find(|user| user.username == payload.username)
            .cloned()
    };

    // Check if user exists
    let user = match user {
        Some(user) => user,
        None => {
            return Err(AppError::Auth(AuthError::InvalidPassword));
        }
    };

    // Verify password
    let password_verified = AuthService::verify_password(&payload.password, &user.password_hash)?;

    if !password_verified {
        return Err(AppError::Auth(AuthError::InvalidPassword));
    }

    // Generate JWT token
    let token = AuthService::create_token(&user.id, &user.username)?;

    // Return token
    Ok((
        StatusCode::OK,
        Json(AuthResponse {
            access_token: token,
            token_type: "Bearer",
            expires_in: 86400, // 24 hours in seconds
        }),
    ))
}

async fn get_user_by_id(
    State(state): State<AppState>,
    Path(user_id): Path<String>,
) -> Result<Json<User>> {
    // Get user from state
    let users = state.users.read().unwrap();
    
    match users.get(&user_id) {
        Some(user) => Ok(Json(user.clone())),
        None => Err(AppError::NotFound(format!("User with ID {} not found", user_id))),
    }
}

async fn send_message(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Json(payload): Json<SendMessageRequest>,
) -> Result<(StatusCode, Json<MessageResponse>)> {
    // Validate request
    if payload.recipient_id.is_empty() || payload.content.is_empty() {
        return Err(AppError::BadRequest("Recipient ID and content are required".to_string()));
    }

    // Check if recipient exists
    {
        let users = state.users.read().unwrap();
        if !users.contains_key(&payload.recipient_id) {
            return Err(AppError::NotFound(format!("Recipient with ID {} not found", payload.recipient_id)));
        }
    }

    // Create a new message
    let message_id = Uuid::new_v4().to_string();
    let sent_at = chrono::Utc::now().to_rfc3339();

    let message = Message {
        id: message_id.clone(),
        sender_id: current_user.user_id.clone(),
        recipient_id: payload.recipient_id.clone(),
        content: payload.content.clone(),
        sent_at,
        received_at: None,
        read_at: None,
    };

    // Store the message
    {
        let mut messages = state.messages.write().unwrap();
        messages.insert(message_id.clone(), message.clone());
    }

    // Broadcast the message to the recipient via WebSocket if they're online
    if state.ws_manager.is_user_online(&payload.recipient_id) {
        let message_json = serde_json::to_string(&message).unwrap_or_default();
        state.ws_manager.broadcast_to_user(&payload.recipient_id, message_json);
        info!("Message broadcasted to user {}", payload.recipient_id);
    }

    info!("Message sent: {}", message_id);

    // Return success
    Ok((
        StatusCode::CREATED,
        Json(MessageResponse {
            id: message.id,
            sender_id: message.sender_id,
            recipient_id: message.recipient_id,
            content: message.content,
            sent_at: message.sent_at,
            received_at: message.received_at,
            read_at: message.read_at,
        }),
    ))
}

async fn get_messages_for_user(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Path(user_id): Path<String>,
) -> Result<Json<Vec<Message>>> {
    // Ensure the current user is requesting their own messages
    if current_user.user_id != user_id {
        return Err(AppError::Forbidden("You can only access your own messages".to_string()));
    }

    // Check if user exists
    {
        let users = state.users.read().unwrap();
        if !users.contains_key(&user_id) {
            return Err(AppError::NotFound(format!("User with ID {} not found", user_id)));
        }
    }

    // Get messages for user
    let messages = state.messages.read().unwrap();
    let user_messages: Vec<Message> = messages
        .values()
        .filter(|msg| msg.recipient_id == user_id || msg.sender_id == user_id)
        .cloned()
        .collect();

    Ok(Json(user_messages))
}

async fn mark_message_as_received(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Path(message_id): Path<String>,
) -> Result<Json<MessageResponse>> {
    // Get the message
    let mut messages = state.messages.write().unwrap();
    let message = messages.get_mut(&message_id).ok_or_else(|| {
        AppError::NotFound(format!("Message with ID {} not found", message_id))
    })?;
    
    // Check if the current user is the recipient of the message
    if message.recipient_id != current_user.user_id {
        return Err(AppError::Forbidden("You can only mark messages sent to you as received".to_string()));
    }
    
    // Update the message
    message.received_at = Some(chrono::Utc::now().to_rfc3339());
    
    // Broadcast status update to sender if online
    if state.ws_manager.is_user_online(&message.sender_id) {
        let status_update = format!(
            "{{\"type\":\"message_received\",\"message_id\":\"{}\",\"user_id\":\"{}\"}}",
            message_id, current_user.user_id
        );
        state.ws_manager.broadcast_to_user(&message.sender_id, status_update);
    }
    
    info!("Message {} marked as received", message_id);
    
    // Return the updated message
    Ok(Json(MessageResponse {
        id: message.id.clone(),
        sender_id: message.sender_id.clone(),
        recipient_id: message.recipient_id.clone(),
        content: message.content.clone(),
        sent_at: message.sent_at.clone(),
        received_at: message.received_at.clone(),
        read_at: message.read_at.clone(),
    }))
}

async fn mark_message_as_read(
    State(state): State<AppState>,
    current_user: CurrentUser,
    Path(message_id): Path<String>,
) -> Result<Json<MessageResponse>> {
    // Get the message
    let mut messages = state.messages.write().unwrap();
    let message = messages.get_mut(&message_id).ok_or_else(|| {
        AppError::NotFound(format!("Message with ID {} not found", message_id))
    })?;
    
    // Check if the current user is the recipient of the message
    if message.recipient_id != current_user.user_id {
        return Err(AppError::Forbidden("You can only mark messages sent to you as read".to_string()));
    }
    
    // Update the message
    message.read_at = Some(chrono::Utc::now().to_rfc3339());
    
    // Make sure received_at is set if not already
    if message.received_at.is_none() {
        message.received_at = message.read_at.clone();
    }
    
    // Broadcast status update to sender if online
    if state.ws_manager.is_user_online(&message.sender_id) {
        let status_update = format!(
            "{{\"type\":\"message_read\",\"message_id\":\"{}\",\"user_id\":\"{}\"}}",
            message_id, current_user.user_id
        );
        state.ws_manager.broadcast_to_user(&message.sender_id, status_update);
    }
    
    info!("Message {} marked as read", message_id);
    
    // Return the updated message
    Ok(Json(MessageResponse {
        id: message.id.clone(),
        sender_id: message.sender_id.clone(),
        recipient_id: message.recipient_id.clone(),
        content: message.content.clone(),
        sent_at: message.sent_at.clone(),
        received_at: message.received_at.clone(),
        read_at: message.read_at.clone(),
    }))
}
