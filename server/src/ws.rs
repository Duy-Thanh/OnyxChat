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
    // Welcome message
    let welcome_msg = format!("Welcome, {}! You are now connected.", user_id);
    if let Err(e) = ws_sender.send(Message::Text(welcome_msg)).await {
        println!("Error sending welcome message: {}", e);
        return;
    }

    // Use select to handle messages from both sources
    loop {
        tokio::select! {
            // Handle outgoing messages from other sources
            msg = receiver.recv() => {
                match msg {
                    Some(message) => {
                        if let Err(e) = ws_sender.send(message).await {
                            println!("Error forwarding message: {}", e);
                            break;
                        }
                    }
                    None => break, // Channel closed
                }
            }
            
            // Handle incoming messages from WebSocket
            result = ws_receiver.next() => {
                match result {
                    Some(Ok(msg)) => {
                        match msg {
                            Message::Text(text) => {
                                println!("Received message from {}: {}", user_id, text);
                                
                                // Check if this is a direct message to another user
                                if let Some(direct_msg) = parse_direct_message(&text) {
                                    let (recipient, content) = direct_msg;
                                    
                                    // Forward the message to the recipient
                                    let dm_json = format!(
                                        "{{\"type\":\"direct_message\",\"from\":\"{}\",\"content\":\"{}\"}}",
                                        user_id, content
                                    );
                                    
                                    let recipients = ws_manager.broadcast_to_user(&recipient, dm_json.clone());
                                    
                                    if recipients > 0 {
                                        // Send confirmation back to sender
                                        let confirm_msg = format!("Message sent to {}", recipient);
                                        if let Err(e) = ws_sender.send(Message::Text(confirm_msg)).await {
                                            println!("Error sending confirmation: {}", e);
                                            break;
                                        }
                                    } else {
                                        // Recipient not online
                                        let error_msg = format!("User {} is not online", recipient);
                                        if let Err(e) = ws_sender.send(Message::Text(error_msg)).await {
                                            println!("Error sending error message: {}", e);
                                            break;
                                        }
                                    }
                                } else {
                                    // Regular echo for non-direct messages
                                    let response = format!("Echo: {}", text);
                                    if let Err(e) = ws_sender.send(Message::Text(response)).await {
                                        println!("Error sending response: {}", e);
                                        break;
                                    }
                                }
                            }
                            Message::Binary(data) => {
                                println!("Received binary data from {}", user_id);
                                // Echo back the binary data
                                if let Err(e) = ws_sender.send(Message::Binary(data)).await {
                                    println!("Error sending binary response: {}", e);
                                    break;
                                }
                            }
                            Message::Ping(data) => {
                                // Respond to ping with pong
                                if let Err(e) = ws_sender.send(Message::Pong(data)).await {
                                    println!("Error sending pong: {}", e);
                                    break;
                                }
                            }
                            Message::Pong(_) => {
                                // Ignore pong messages
                            }
                            Message::Close(_) => {
                                println!("Received close frame from {}", user_id);
                                break;
                            }
                        }
                    }
                    Some(Err(e)) => {
                        println!("WebSocket error: {}", e);
                        break;
                    }
                    None => break, // WebSocket closed
                }
            }
        }
    }

    // Connection closed, clean up
    ws_manager.remove_connection(&user_id, &connection_id);
    println!("WebSocket connection closed for user {}", user_id);
}

// Parse direct message format: @username:message
fn parse_direct_message(text: &str) -> Option<(String, String)> {
    if text.starts_with('@') {
        if let Some(colon_pos) = text.find(':') {
            if colon_pos > 1 {
                let recipient = text[1..colon_pos].trim().to_string();
                let content = text[colon_pos + 1..].trim().to_string();
                
                if !recipient.is_empty() && !content.is_empty() {
                    return Some((recipient, content));
                }
            }
        }
    }
    
    None
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