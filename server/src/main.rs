mod config;
mod db;
mod error;
mod handlers;
mod middleware;
mod models;

use std::{net::SocketAddr, sync::Arc};

use axum::{
    error_handling::HandleErrorLayer,
    http::StatusCode,
    routing::{get, post, delete},
    Router,
};
use color_eyre::Result;
use sqlx::postgres::PgPoolOptions;
use tower::ServiceBuilder;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use tracing::info;

use crate::{
    config::AppConfig,
    handlers::{
        auth::{login, logout, refresh_token, register, validate_token},
        crypto::{get_prekey_bundle, upload_prekey_bundle},
        message::{
            delete_message, get_message, get_messages, get_messages_with_user,
            mark_message_read, mark_message_received, send_message,
        },
        user::{delete_user, get_current_user, get_user_by_username, get_user_profile, update_user},
    },
    models::AppState,
};

const API_VERSION: &str = "v1";

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize tracing
    tracing_subscriber::fmt::init();

    // Load configuration
    let config = AppConfig::load()?;
    info!("Configuration loaded");

    // Setup database connection
    let db_pool = PgPoolOptions::new()
        .max_connections(config.db_pool_max_size)
        .connect(&config.database_url)
        .await?;
    info!("Database connection established");

    // Run migrations
    sqlx::migrate!("./migrations").run(&db_pool).await?;
    info!("Database migrations applied");

    // Create app state
    let app_state = AppState::new(db_pool, config.clone());

    // Configure CORS
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods(Any)
        .allow_headers(Any);

    // Build router
    let app = Router::new()
        .route(&format!("/{API_VERSION}/health"), get(health_check))
        .nest(
            &format!("/{API_VERSION}/auth"),
            auth_routes(),
        )
        .nest(
            &format!("/{API_VERSION}/users"),
            user_routes(),
        )
        .nest(
            &format!("/{API_VERSION}/messages"),
            message_routes(),
        )
        .nest(
            &format!("/{API_VERSION}/crypto"),
            crypto_routes(),
        )
        .layer(
            ServiceBuilder::new()
                .layer(HandleErrorLayer::new(|_| async {
                    StatusCode::INTERNAL_SERVER_ERROR
                }))
                .layer(TraceLayer::new_for_http())
                .layer(cors),
        )
        .with_state(app_state);

    // Start server
    let addr = SocketAddr::from(([0, 0, 0, 0], config.port));
    info!("Server listening on {}", addr);
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await?;

    Ok(())
}

async fn health_check() -> StatusCode {
    StatusCode::OK
}

fn auth_routes() -> Router<AppState> {
    Router::new()
        .route("/register", post(register))
        .route("/login", post(login))
        .route("/refresh", post(refresh_token))
        .route("/logout", post(logout))
        .route("/validate", get(validate_token))
}

fn user_routes() -> Router<AppState> {
    Router::new()
        .route("/me", get(get_current_user))
        .route("/me", post(update_user))
        .route("/me", delete(delete_user))
        .route("/:user_id", get(get_user_profile))
        .route("/by-username/:username", get(get_user_by_username))
}

fn message_routes() -> Router<AppState> {
    Router::new()
        .route("/", post(send_message))
        .route("/", get(get_messages))
        .route("/:message_id", get(get_message))
        .route("/:message_id/received", post(mark_message_received))
        .route("/:message_id/read", post(mark_message_read))
        .route("/:message_id", delete(delete_message))
        .route("/with/:user_id", get(get_messages_with_user))
}

fn crypto_routes() -> Router<AppState> {
    Router::new()
        .route("/prekey-bundle", post(upload_prekey_bundle))
        .route("/prekey-bundle/:user_id", get(get_prekey_bundle))
} 