use axum::{
    routing::{get, post},
    http::StatusCode,
    Json,
    Router,
    extract::{State, Path},
};
use serde::{Deserialize, Serialize};
use std::{
    net::SocketAddr,
    collections::HashMap,
    sync::{Arc, RwLock},
};
use tower_http::cors::CorsLayer;
use tracing::info;
use uuid::Uuid;

// Import modules
mod auth;
mod error;
mod crypto;
mod ws;

use auth::{AuthError, AuthResponse, AuthService, CurrentUser, LoginRequest};
use error::{AppError, Result};
use crypto::{UserKey, OneTimePrekey, crypto_routes};
use ws::{create_ws_manager, WebSocketManager, ws_routes};

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
}

// Application state
#[derive(Clone)]
struct AppState {
    users: Arc<RwLock<HashMap<String, User>>>,
    messages: Arc<RwLock<HashMap<String, Message>>>,
    user_keys: Arc<RwLock<HashMap<String, UserKey>>>,
    prekeys: Arc<RwLock<HashMap<String, OneTimePrekey>>>,
    ws_manager: Arc<WebSocketManager>,
}

#[tokio::main]
async fn main() {
    // Initialize tracing
    tracing_subscriber::fmt::init();
    info!("Starting OnyxChat server");

    // Create WebSocket manager
    let ws_manager = Arc::new(create_ws_manager());

    // Create app state
    let app_state = AppState {
        users: Arc::new(RwLock::new(HashMap::new())),
        messages: Arc::new(RwLock::new(HashMap::new())),
        user_keys: Arc::new(RwLock::new(HashMap::new())),
        prekeys: Arc::new(RwLock::new(HashMap::new())),
        ws_manager: ws_manager.clone(),
    };

    // Build our application with routes
    let app = Router::new()
        .route("/", get(hello_world))
        .route("/health", get(health_check))
        // Auth routes
        .route("/api/users", post(register_user))
        .route("/api/users/:id", get(get_user_by_id))
        .route("/api/auth/login", post(login))
        // Message routes
        .route("/api/messages", post(send_message))
        .route("/api/messages/:user_id", get(get_messages_for_user))
        // E2EE crypto routes
        .nest("/api/crypto", crypto_routes())
        // WebSocket routes
        .nest("/api/ws", ws_routes())
        .layer(CorsLayer::permissive())
        .with_state(app_state);

    // Run the server
    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    info!("Server listening on {}", addr);
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();
}

async fn hello_world() -> &'static str {
    "Hello, World from OnyxChat!"
}

async fn health_check() -> &'static str {
    "OK"
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
    };

    // Store the message
    {
        let mut messages = state.messages.write().unwrap();
        messages.insert(message_id.clone(), message.clone());
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
