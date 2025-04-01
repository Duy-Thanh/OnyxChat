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
use tracing::{info, error};
use uuid::Uuid;

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

// Error response model
#[derive(Debug, Serialize)]
struct ErrorResponse {
    error: String,
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
}

#[tokio::main]
async fn main() {
    // Initialize tracing
    tracing_subscriber::fmt::init();
    info!("Starting OnyxChat server");

    // Create app state
    let app_state = AppState {
        users: Arc::new(RwLock::new(HashMap::new())),
        messages: Arc::new(RwLock::new(HashMap::new())),
    };

    // Build our application with routes
    let app = Router::new()
        .route("/", get(hello_world))
        .route("/health", get(health_check))
        .route("/api/users", post(register_user))
        .route("/api/users/:id", get(get_user_by_id))
        .route("/api/messages", post(send_message))
        .route("/api/messages/:user_id", get(get_messages_for_user))
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
) -> Result<(StatusCode, Json<UserCreatedResponse>), (StatusCode, Json<ErrorResponse>)> {
    // Validate request
    if payload.username.is_empty() || payload.email.is_empty() || payload.password.is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "Username, email, and password are required".to_string(),
            }),
        ));
    }

    // Check if username already exists
    {
        let users = state.users.read().unwrap();
        if users.values().any(|user| user.username == payload.username) {
            return Err((
                StatusCode::BAD_REQUEST,
                Json(ErrorResponse {
                    error: "Username already exists".to_string(),
                }),
            ));
        }
    }

    // In a real app, we would hash the password here
    let password_hash = payload.password.clone(); // Placeholder for real hashing

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

async fn get_user_by_id(
    State(state): State<AppState>,
    Path(user_id): Path<String>,
) -> Result<Json<User>, (StatusCode, Json<ErrorResponse>)> {
    // Get user from state
    let users = state.users.read().unwrap();
    
    match users.get(&user_id) {
        Some(user) => Ok(Json(user.clone())),
        None => Err((
            StatusCode::NOT_FOUND,
            Json(ErrorResponse {
                error: format!("User with ID {} not found", user_id),
            }),
        )),
    }
}

async fn send_message(
    State(state): State<AppState>,
    Json(payload): Json<SendMessageRequest>,
) -> Result<(StatusCode, Json<MessageResponse>), (StatusCode, Json<ErrorResponse>)> {
    // Validate request
    if payload.recipient_id.is_empty() || payload.content.is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(ErrorResponse {
                error: "Recipient ID and content are required".to_string(),
            }),
        ));
    }

    // Check if sender and recipient exist
    {
        let users = state.users.read().unwrap();
        if !users.contains_key(&payload.recipient_id) {
            return Err((
                StatusCode::NOT_FOUND,
                Json(ErrorResponse {
                    error: format!("Recipient with ID {} not found", payload.recipient_id),
                }),
            ));
        }
    }

    // Create a new message (using placeholder sender ID as we don't have auth yet)
    let message_id = Uuid::new_v4().to_string();
    let sent_at = chrono::Utc::now().to_rfc3339();
    let sender_id = "system"; // Placeholder - in a real app, this would come from authenticated user

    let message = Message {
        id: message_id.clone(),
        sender_id: sender_id.to_string(),
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
    Path(user_id): Path<String>,
) -> Result<Json<Vec<Message>>, (StatusCode, Json<ErrorResponse>)> {
    // Check if user exists
    {
        let users = state.users.read().unwrap();
        if !users.contains_key(&user_id) {
            return Err((
                StatusCode::NOT_FOUND,
                Json(ErrorResponse {
                    error: format!("User with ID {} not found", user_id),
                }),
            ));
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
