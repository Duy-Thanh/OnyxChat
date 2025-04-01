use axum::{routing::get, Router};
use std::net::SocketAddr;
use tower_http::cors::CorsLayer;
use tracing::info;

#[tokio::main]
async fn main() {
    // Initialize tracing
    tracing_subscriber::fmt::init();
    info!("Starting OnyxChat server");

    // Build our application with a route
    let app = Router::new()
        .route("/", get(hello_world))
        .route("/health", get(health_check))
        .layer(CorsLayer::permissive());

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
