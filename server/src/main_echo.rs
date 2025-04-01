use std::net::SocketAddr;

use axum::{
    extract::ws::{Message, WebSocket, WebSocketUpgrade},
    response::IntoResponse,
    routing::get,
    Router,
};
use futures::{sink::SinkExt, stream::StreamExt};
use log::{error, info};
use tower_http::cors::CorsLayer;

#[tokio::main]
async fn main() {
    // Initialize logger
    env_logger::Builder::new()
        .filter_level(log::LevelFilter::Info)
        .init();
    
    info!("Starting WebSocket echo server");

    // Create router
    let app = Router::new()
        .route("/ws", get(ws_handler))
        .route("/", get(|| async { "WebSocket Echo Server - Connect to /ws" }))
        .layer(CorsLayer::permissive());

    // Start server
    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    info!("Listening on {}", addr);
    
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();
}

// WebSocket handler
async fn ws_handler(ws: WebSocketUpgrade) -> impl IntoResponse {
    ws.on_upgrade(handle_socket)
}

// Handle WebSocket connection
async fn handle_socket(socket: WebSocket) {
    info!("New WebSocket connection");
    
    let (mut sender, mut receiver) = socket.split();
    
    // Echo all messages back to the client
    tokio::spawn(async move {
        while let Some(Ok(message)) = receiver.next().await {
            match message {
                Message::Text(text) => {
                    info!("Received text message: {}", text);
                    if let Err(e) = sender.send(Message::Text(format!("Echo: {}", text))).await {
                        error!("Error sending message: {}", e);
                        break;
                    }
                },
                Message::Binary(data) => {
                    info!("Received binary message");
                    if let Err(e) = sender.send(Message::Binary(data)).await {
                        error!("Error sending binary data: {}", e);
                        break;
                    }
                },
                Message::Ping(ping) => {
                    info!("Received ping");
                    if let Err(e) = sender.send(Message::Pong(ping)).await {
                        error!("Error sending pong: {}", e);
                        break;
                    }
                },
                Message::Pong(_) => {
                    info!("Received pong");
                },
                Message::Close(_) => {
                    info!("Received close message");
                    break;
                },
            }
        }
        
        info!("WebSocket connection closed");
    });
} 