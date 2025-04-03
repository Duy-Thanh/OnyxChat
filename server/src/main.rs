use std::sync::Arc;
use std::net::SocketAddr;

use axum::{
    Router,
    routing::{get, post},
    http::StatusCode,
    extract::{State, Path},
    response::IntoResponse,
    Json,
};
use sqlx::postgres::PgPoolOptions;
use serde::{Serialize, Deserialize};
use log::info;
use dotenv::dotenv;
use env_logger;
use tower_http::cors::{Any, CorsLayer};
use uuid::Uuid;

mod models;
mod handlers;
mod middleware;
mod ws;
mod error;
mod config;

use crate::models::{
    User, Message, LoginRequest, AuthResponse, 
    UserCreatedResponse, MessageResponse, AuthService
};
use crate::handlers::auth;
use crate::middleware::auth::{CurrentUser, auth_middleware};
use crate::error::{AppError, Result};
use crate::middleware::auth::AuthError;
use crate::ws::WebSocketManager;

// Application state
#[derive(Clone)]
pub struct AppState {
    pub db: Option<sqlx::PgPool>,
    pub config: config::AppConfig,
    pub ws_manager: Arc<WebSocketManager>,
}

impl AppState {
    pub fn new(db: Option<sqlx::PgPool>, ws_manager: Arc<WebSocketManager>) -> Self {
        Self {
            db,
            config: config::AppConfig::from_env(),
            ws_manager,
        }
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize environment
    dotenv().ok();
    env_logger::init();
    
    // Try to connect to the database
    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://postgres:postgres@localhost/onyxchat".to_string());
    
    let db = match PgPoolOptions::new()
        .max_connections(10)
        .connect(&database_url)
        .await {
            Ok(pool) => {
                info!("Connected to database successfully");
                Some(pool)
            },
            Err(e) => {
                eprintln!("Failed to connect to database: {}", e);
                None
            }
        };
    
    // Create WebSocket manager
    let ws_manager = Arc::new(WebSocketManager::new());
    
    // Create application state
    let app_state = AppState::new(db, ws_manager);
    
    // CORS configuration
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    // Create router
    let app = Router::new()
        // Health check endpoint
        .route("/api/health", get(health_check))
        .route("/api/hello", get(hello_world))
        
        // User routes
        .route("/api/users", post(auth::register))
        .route("/api/users/:user_id", get(get_user_by_id))
        
        // Auth routes
        .route("/api/auth/login", post(auth::login))
        
        // Message routes
        .route("/api/messages", post(send_message))
        .route("/api/messages/user/:user_id", get(get_messages_for_user))
        .route("/api/messages/:message_id/received", post(mark_message_as_received))
        .route("/api/messages/:message_id/read", post(mark_message_as_read))
        
        // WebSocket routes
        .route("/ws/:user_id", get(ws::ws_handler))
        
        // Apply middleware
        .layer(cors)
        .layer(axum::middleware::from_fn(auth_middleware))
        
        // Add app state
        .with_state(app_state);

    // Start server
    let addr = SocketAddr::from(([0, 0, 0, 0], 8081));
    info!("Starting server on {}", addr);
    
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();

    Ok(())
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
    Json(payload): Json<models::user::CreateUserRequest>,
) -> Result<(StatusCode, Json<UserCreatedResponse>)> {
    // Validate request
    if payload.username.is_empty() || payload.email.is_empty() || payload.password.is_empty() {
        return Err(AppError::bad_request("Username, email, and password are required"));
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
    State(_state): State<AppState>,
    Json(payload): Json<LoginRequest>,
) -> Result<(StatusCode, Json<AuthResponse>)> {
    // For demo purposes
    // In a real implementation this would verify against a database
    
    // Verify password
    let password_verified = AuthService::verify_password(&payload.password, "dummy_hash")?;

    if !password_verified {
        return Err(AppError::Auth(crate::middleware::auth::AuthError::InvalidPassword));
    }

    // Generate JWT token
    let user_id = "demo_user_id"; // This would come from the database
    let token = AuthService::create_token(user_id, &payload.username)?;

    // Return token
    Ok((
        StatusCode::OK,
        Json(AuthResponse {
            token,
            refresh_token: "dummy_refresh_token".to_string(),
            token_type: "Bearer".to_string(),
            expires_in: 86400, // 24 hours in seconds
        }),
    ))
}

async fn get_user_by_id(
    State(_state): State<AppState>,
    Path(user_id): Path<String>,
) -> Result<Json<User>> {
    // This would fetch from the database in a real implementation
    
    // For demo purposes, return a dummy user
    let user = User {
        id: user_id.clone(),
        username: "demo_user".to_string(),
        email: "demo@example.com".to_string(),
        password_hash: "".to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
    };
    
    Ok(Json(user))
}

// Request model for sending a message
#[derive(Debug, Deserialize)]
struct SendMessageRequest {
    recipient_id: String,
    content: String,
}

async fn send_message(
    State(_state): State<AppState>,
    current_user: CurrentUser,
    Json(payload): Json<SendMessageRequest>,
) -> Result<(StatusCode, Json<MessageResponse>)> {
    // Validate request
    if payload.recipient_id.is_empty() || payload.content.is_empty() {
        return Err(AppError::bad_request("Recipient ID and content are required"));
    }

    // Create a new message
    let message_id = Uuid::new_v4().to_string();
    let sent_at = chrono::Utc::now().to_rfc3339();

    let message = Message {
        id: message_id.clone(),
        sender_id: current_user.id,
        recipient_id: payload.recipient_id,
        content: payload.content,
        sent_at: sent_at.clone(),
        received_at: None,
        read_at: None,
    };

    // In a real implementation, save to database and notify via WebSocket

    // Return the created message
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
    State(_state): State<AppState>,
    current_user: CurrentUser,
    Path(user_id): Path<String>,
) -> Result<Json<Vec<Message>>> {
    // In a real implementation, this would verify permissions and fetch from database
    
    if current_user.id != user_id {
        return Err(AppError::forbidden("You can only access your own messages"));
    }
    
    // For demo purposes, return an empty list
    Ok(Json(Vec::new()))
}

async fn mark_message_as_received(
    State(_state): State<AppState>,
    current_user: CurrentUser,
    Path(message_id): Path<String>,
) -> Result<Json<MessageResponse>> {
    // In a real implementation, this would update the message in the database
    
    // Create a dummy message for the response
    let message = Message {
        id: message_id,
        sender_id: "other_user".to_string(),
        recipient_id: current_user.id,
        content: "Demo message".to_string(),
        sent_at: chrono::Utc::now().to_rfc3339(),
        received_at: Some(chrono::Utc::now().to_rfc3339()),
        read_at: None,
    };
    
    Ok(Json(MessageResponse {
        id: message.id,
        sender_id: message.sender_id,
        recipient_id: message.recipient_id,
        content: message.content,
        sent_at: message.sent_at,
        received_at: message.received_at,
        read_at: message.read_at,
    }))
}

async fn mark_message_as_read(
    State(_state): State<AppState>,
    current_user: CurrentUser,
    Path(message_id): Path<String>,
) -> Result<Json<MessageResponse>> {
    // In a real implementation, this would update the message in the database
    
    // Create a dummy message for the response
    let message = Message {
        id: message_id,
        sender_id: "other_user".to_string(),
        recipient_id: current_user.id,
        content: "Demo message".to_string(),
        sent_at: chrono::Utc::now().to_rfc3339(),
        received_at: Some(chrono::Utc::now().to_rfc3339()),
        read_at: Some(chrono::Utc::now().to_rfc3339()),
    };
    
    Ok(Json(MessageResponse {
        id: message.id,
        sender_id: message.sender_id,
        recipient_id: message.recipient_id,
        content: message.content,
        sent_at: message.sent_at,
        received_at: message.received_at,
        read_at: message.read_at,
    }))
}
