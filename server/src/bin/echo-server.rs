use axum::{
    extract::ws::{Message, WebSocket, WebSocketUpgrade},
    response::IntoResponse,
    routing::get,
    Router,
};
use futures::{sink::SinkExt, stream::StreamExt};
use std::net::SocketAddr;
use tokio::net::TcpListener;

#[tokio::main]
async fn main() {
    // Initialize tracing for better logging
    tracing_subscriber::fmt::init();

    // Build our application with a single route
    let app = Router::new()
        .route("/", get(|| async { "Echo Server is running!" }))
        .route("/ws", get(ws_handler));

    // Run the server
    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    tracing::info!("Echo server listening on {}", addr);
    
    // Create a TCP listener
    let listener = TcpListener::bind(addr).await.unwrap();
    
    // Start serving connections
    axum::serve(listener, app).await.unwrap();
}

/// Handler for WebSocket connections
async fn ws_handler(ws: WebSocketUpgrade) -> impl IntoResponse {
    ws.on_upgrade(handle_socket)
}

/// Handles a connected WebSocket
async fn handle_socket(mut socket: WebSocket) {
    tracing::info!("New WebSocket connection established");

    // Send a welcome message
    if let Err(e) = socket.send(Message::Text("Welcome to OnyxChat Echo Server!".to_string())).await {
        tracing::error!("Error sending welcome message: {}", e);
        return;
    }

    // This task will receive messages from the client and echo them back
    while let Some(msg) = socket.recv().await {
        if let Ok(msg) = msg {
            match msg {
                Message::Text(text) => {
                    tracing::info!("Received message: {}", text);
                    
                    // Echo the message back
                    if let Err(e) = socket.send(Message::Text(text)).await {
                        tracing::error!("Error sending message: {}", e);
                        break;
                    }
                }
                Message::Binary(data) => {
                    // Echo binary data back
                    if let Err(e) = socket.send(Message::Binary(data)).await {
                        tracing::error!("Error sending binary: {}", e);
                        break;
                    }
                }
                Message::Ping(data) => {
                    // Respond to pings
                    if let Err(e) = socket.send(Message::Pong(data)).await {
                        tracing::error!("Error sending pong: {}", e);
                        break;
                    }
                }
                Message::Pong(_) => {
                    // Ignore pongs
                }
                Message::Close(c) => {
                    // Client wants to close connection
                    tracing::info!("Client requested to close the connection");
                    if let Some(cf) = c {
                        tracing::info!("Close frame: code={}, reason={}", cf.code, cf.reason);
                    }
                    
                    // Acknowledge the close request by sending a close frame back
                    if let Err(e) = socket.send(Message::Close(None)).await {
                        tracing::error!("Error sending close frame: {}", e);
                    }
                    break;
                }
            }
        } else {
            // Client disconnected
            tracing::info!("Client disconnected");
            break;
        }
    }

    tracing::info!("WebSocket connection closed");
} 