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

// Use everything from our lib
use onyxchat_server::*;

// Application state
#[derive(Clone)]
pub struct AppState {
    pub db: Option<sqlx::PgPool>,
    pub config: Arc<config::AppConfig>,
    pub ws_manager: Arc<WebSocketManager>,
}

impl AppState {
    pub fn new(db: Option<sqlx::PgPool>, ws_manager: Arc<WebSocketManager>) -> Self {
        Self {
            db,
            config: Arc::new(config::AppConfig::from_env()),
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
        .route("/api/users", post(handlers::auth::register))
        .route("/api/users/:user_id", get(handlers::user::get_user_by_id))
        
        // Auth routes
        .route("/api/auth/login", post(handlers::auth::login))
        
        // Message routes
        .route("/api/messages", post(handlers::message::send_message))
        .route("/api/messages/user/:user_id", get(handlers::message::get_messages_for_user))
        
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
