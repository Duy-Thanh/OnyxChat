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
use serde_json::json;
use log::info;
use dotenv::dotenv;
use env_logger;
use tower_http::cors::{Any, CorsLayer};
use uuid::Uuid;

// Import everything from the crate
use onyxchat_server::*;

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize environment
    dotenv().ok();
    env_logger::init();
    
    // Create WebSocket manager
    let ws_manager = Arc::new(WebSocketManager::new());
    
    // Create application state with None for db - our mock implementations don't need it
    let app_state = AppState::new(None, ws_manager);
    
    // CORS configuration
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    // Create router
    let app = Router::new()
        // Basic routes
        .route("/api/health", get(health_check))
        .route("/api/hello", get(hello_world))
        
        // Apply middleware
        .layer(cors)
        
        // Add app state
        .with_state(app_state);

    // Start server
    let addr = SocketAddr::from(([0, 0, 0, 0], 8081));
    info!("Starting mock server on {}", addr);
    
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

async fn hello_world() -> impl IntoResponse {
    Json(json!({ 
        "message": "Hello, World from OnyxChat Mock Server!",
        "status": "success",
    }))
} 