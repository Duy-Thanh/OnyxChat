use axum::{
    extract::{ws::{Message, WebSocket, WebSocketUpgrade}, Path, State},
    response::IntoResponse,
    routing::get,
    Router,
};
use futures::{sink::SinkExt, stream::StreamExt};
use serde::{Deserialize, Serialize};
use std::{
    collections::{HashMap, HashSet},
    sync::{Arc, Mutex, RwLock},
};
use tokio::sync::broadcast;
use tracing::{error, info};
use uuid::Uuid;
use tokio::{sync::mpsc, task::JoinHandle};
use tokio::net::TcpStream;
use futures::stream::{SplitSink, SplitStream};

use crate::{
    error::{AppError, Result},
    AppState,
    Message as DbMessage,
    middleware::auth::CurrentUser,
};

// Maximum number of messages to keep in history
const MAX_MESSAGES_HISTORY: usize = 100;

// WebSocket message types
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", content = "data")]
enum WebSocketMessage {
    Text { content: String, sender_id: String },
    Typing { is_typing: bool, user_id: String },
    Read { message_ids: Vec<String>, user_id: String },
    Delivered { message_ids: Vec<String>, user_id: String },
    Status { user_id: String, status: UserStatus },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
enum UserStatus {
    Online,
    Offline,
    Away,
}

// Connected clients by user ID
type ConnectedClients = Arc<RwLock<HashMap<String, HashSet<String>>>>;

// Active connections by connection ID
type ActiveConnections = Arc<Mutex<HashMap<String, broadcast::Sender<String>>>>;

// Message history by user ID
type MessageHistory = Arc<RwLock<HashMap<String, Vec<String>>>>;

// WebSocket message manager to track active connections
pub struct WebSocketManager {
    connections: Arc<RwLock<HashMap<String, HashMap<String, mpsc::UnboundedSender<Message>>>>>,
}

impl WebSocketManager {
    pub fn new() -> Self {
        WebSocketManager {
            connections: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    // Add a connection for a user
    pub fn add_connection(&self, user_id: &str, conn_id: &str, sender: mpsc::UnboundedSender<Message>) {
        let mut write_guard = self.connections.write().unwrap();
        let user_connections = write_guard
            .entry(user_id.to_string())
            .or_insert_with(HashMap::new);
        user_connections.insert(conn_id.to_string(), sender);
    }

    // Remove a connection
    pub fn remove_connection(&self, user_id: &str, conn_id: &str) {
        let mut write_guard = self.connections.write().unwrap();
        if let Some(user_connections) = write_guard.get_mut(user_id) {
            user_connections.remove(conn_id);
            if user_connections.is_empty() {
                write_guard.remove(user_id);
            }
        }
    }

    // Check if a user is online
    pub fn is_user_online(&self, user_id: &str) -> bool {
        let read_guard = self.connections.read().unwrap();
        read_guard.contains_key(user_id)
    }

    // Send a message to a specific user
    pub fn broadcast_to_user(&self, user_id: &str, message: String) -> usize {
        let read_guard = self.connections.read().unwrap();
        
        let mut sent_count = 0;
        if let Some(user_connections) = read_guard.get(user_id) {
            for sender in user_connections.values() {
                if sender.send(Message::Text(message.clone())).is_ok() {
                    sent_count += 1;
                }
            }
        }
        
        sent_count
    }

    // Broadcast a message to all connected users
    pub fn broadcast_all(&self, message: String) -> usize {
        let read_guard = self.connections.read().unwrap();
        
        let mut sent_count = 0;
        for user_connections in read_guard.values() {
            for sender in user_connections.values() {
                if sender.send(Message::Text(message.clone())).is_ok() {
                    sent_count += 1;
                }
            }
        }
        
        sent_count
    }

    // Broadcast user status to all other connections
    pub fn broadcast_user_status(&self, user_id: &str, status: &str) {
        let status_message = format!(
            "{{\"type\":\"user_status\",\"user_id\":\"{}\",\"status\":\"{}\"}}",
            user_id, status
        );
        
        let read_guard = self.connections.read().unwrap();
        
        for (other_id, user_connections) in read_guard.iter() {
            if other_id != user_id {
                for sender in user_connections.values() {
                    let _ = sender.send(Message::Text(status_message.clone()));
                }
            }
        }
    }
}

// Routes setup
pub fn ws_routes() -> Router<AppState> {
    Router::new()
        .route("/chat/:user_id", get(ws_handler))
        .route("/echo", get(ws_echo_handler))
}

// WebSocket handler
pub async fn ws_handler(
    ws: WebSocketUpgrade,
    Path(user_id): Path<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_socket(socket, user_id, state))
}

// Handle the WebSocket connection
async fn handle_socket(socket: WebSocket, user_id: String, state: AppState) {
    let (sender, receiver) = mpsc::unbounded_channel();
    
    // Generate a unique connection ID
    let connection_id = uuid::Uuid::new_v4().to_string();
    
    // Clone user_id for logging
    let user_id_clone = user_id.clone();
    let connection_id_clone = connection_id.clone();
    
    // Register the connection
    state.ws_manager.add_connection(&user_id, &connection_id, sender);
    
    // Log connection
    println!("User {} connected with connection ID {}", user_id, connection_id);
    
    // Broadcast that the user is online
    state.ws_manager.broadcast_user_status(&user_id, "online");
    
    // Split the socket
    let (ws_sender, ws_receiver) = socket.split();
    
    // Process the socket
    process_socket(ws_sender, ws_receiver, receiver, user_id.clone(), connection_id.clone(), state.ws_manager.clone()).await;
    
    // Clean up when connection ends
    state.ws_manager.remove_connection(&user_id, &connection_id);
    
    // Broadcast that the user is offline
    state.ws_manager.broadcast_user_status(&user_id, "offline");
    
    // Log disconnection
    println!("User {} disconnected with connection ID {}", user_id_clone, connection_id_clone);
}

// Process the WebSocket connection
async fn process_socket(
    mut ws_sender: SplitSink<WebSocket, Message>,
    mut ws_receiver: SplitStream<WebSocket>,
    mut receiver: mpsc::UnboundedReceiver<Message>,
    user_id: String,
    connection_id: String,
    ws_manager: Arc<WebSocketManager>,
) {
    use tokio::select;
    
    // Send a welcome message
    let welcome_msg = format!("{{\"type\":\"welcome\",\"message\":\"Welcome, User {}!\"}}", user_id);
    let _ = ws_sender.send(Message::Text(welcome_msg)).await;
    
    loop {
        select! {
            // Handle outgoing messages
            Some(msg) = receiver.recv() => {
                if ws_sender.send(msg).await.is_err() {
                    break;
                }
            }
            
            // Handle incoming messages
            Some(result) = ws_receiver.next() => {
                match result {
                    Ok(msg) => {
                        match msg {
                            Message::Text(text) => {
                                // Parse the message and handle it
                                if let Ok(json) = serde_json::from_str::<serde_json::Value>(&text) {
                                    if let Some(msg_type) = json.get("type").and_then(|v| v.as_str()) {
                                        match msg_type {
                                            "ping" => {
                                                let _ = ws_sender.send(Message::Text(String::from("{\"type\":\"pong\"}"))).await;
                                            }
                                            "message" => {
                                                // Handle a chat message
                                                if let (Some(recipient_id), Some(content)) = (
                                                    json.get("recipient_id").and_then(|v| v.as_str()),
                                                    json.get("content").and_then(|v| v.as_str()),
                                                ) {
                                                    let message_json = format!(
                                                        "{{\"type\":\"message\",\"sender_id\":\"{}\",\"content\":\"{}\"}}",
                                                        user_id, content
                                                    );
                                                    ws_manager.broadcast_to_user(recipient_id, message_json);
                                                }
                                            }
                                            "typing" => {
                                                // Handle typing indicator
                                                if let Some(recipient_id) = json.get("recipient_id").and_then(|v| v.as_str()) {
                                                    let typing_json = format!(
                                                        "{{\"type\":\"typing\",\"user_id\":\"{}\"}}",
                                                        user_id
                                                    );
                                                    ws_manager.broadcast_to_user(recipient_id, typing_json);
                                                }
                                            }
                                            _ => {
                                                // Unknown message type, echo it back
                                                let _ = ws_sender.send(Message::Text(text)).await;
                                            }
                                        }
                                    }
                                } else {
                                    // If not valid JSON, echo it back
                                    let _ = ws_sender.send(Message::Text(text)).await;
                                }
                            }
                            Message::Binary(data) => {
                                // Echo back binary data
                                let _ = ws_sender.send(Message::Binary(data)).await;
                            }
                            Message::Ping(data) => {
                                // Respond to ping with pong
                                let _ = ws_sender.send(Message::Pong(data)).await;
                            }
                            Message::Pong(_) => {
                                // Ignore pong responses
                            }
                            Message::Close(_) => {
                                // Close the connection
                                break;
                            }
                        }
                    }
                    Err(_) => {
                        // Connection error, break the loop
                        break;
                    }
                }
            }
            
            // No more messages or socket closed
            else => break,
        }
    }
}

// Simple WebSocket echo handler that doesn't require authentication
async fn ws_echo_handler(
    ws: WebSocketUpgrade,
    State(_state): State<AppState>,
) -> impl IntoResponse {
    ws.on_upgrade(handle_echo_socket)
}

// Handle echo WebSocket
async fn handle_echo_socket(socket: WebSocket) {
    let (mut sender, mut receiver) = socket.split();

    // Echo all messages back to the client
    tokio::spawn(async move {
        while let Some(Ok(message)) = receiver.next().await {
            match message {
                Message::Text(text) => {
                    info!("Echo received: {}", text);
                    if sender.send(Message::Text(format!("Echo: {}", text))).await.is_err() {
                        break;
                    }
                }
                Message::Binary(data) => {
                    if sender.send(Message::Binary(data)).await.is_err() {
                        break;
                    }
                }
                Message::Ping(ping) => {
                    if sender.send(Message::Pong(ping)).await.is_err() {
                        break;
                    }
                }
                Message::Pong(_) => {}
                Message::Close(_) => break,
            }
        }
    });
} 