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

use crate::{
    auth::CurrentUser,
    error::{AppError, Result},
    AppState,
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

// WebSocket manager
pub struct WebSocketManager {
    clients: ConnectedClients,
    connections: ActiveConnections,
    history: MessageHistory,
}

impl WebSocketManager {
    pub fn new() -> Self {
        WebSocketManager {
            clients: Arc::new(RwLock::new(HashMap::new())),
            connections: Arc::new(Mutex::new(HashMap::new())),
            history: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    // Add a connection for a user
    pub fn add_connection(&self, user_id: &str, connection_id: &str, tx: broadcast::Sender<String>) {
        // Add to active connections
        {
            let mut connections = self.connections.lock().unwrap();
            connections.insert(connection_id.to_string(), tx.clone());
        }

        // Add to connected clients
        {
            let mut clients = self.clients.write().unwrap();
            let user_connections = clients.entry(user_id.to_string()).or_insert_with(HashSet::new);
            user_connections.insert(connection_id.to_string());
        }

        info!("User {} connected with connection {}", user_id, connection_id);
    }

    // Remove a connection
    pub fn remove_connection(&self, user_id: &str, connection_id: &str) {
        // Remove from active connections
        {
            let mut connections = self.connections.lock().unwrap();
            connections.remove(connection_id);
        }

        // Remove from connected clients
        {
            let mut clients = self.clients.write().unwrap();
            if let Some(user_connections) = clients.get_mut(user_id) {
                user_connections.remove(connection_id);
                if user_connections.is_empty() {
                    clients.remove(user_id);
                }
            }
        }

        info!("User {} disconnected with connection {}", user_id, connection_id);
    }

    // Broadcast a message to all connections of a user
    pub fn broadcast_to_user(&self, user_id: &str, message: String) {
        // Get all connection IDs for the user
        let connection_ids = {
            let clients = self.clients.read().unwrap();
            match clients.get(user_id) {
                Some(connections) => connections.clone(),
                None => return,
            }
        };

        // Add to message history
        self.add_to_history(user_id, message.clone());

        // Send to all connections
        let connections = self.connections.lock().unwrap();
        for connection_id in connection_ids {
            if let Some(tx) = connections.get(&connection_id) {
                if let Err(err) = tx.send(message.clone()) {
                    error!("Failed to send message to {}: {}", connection_id, err);
                }
            }
        }
    }

    // Check if user is online
    pub fn is_user_online(&self, user_id: &str) -> bool {
        let clients = self.clients.read().unwrap();
        clients.contains_key(user_id)
    }

    // Add message to history
    fn add_to_history(&self, user_id: &str, message: String) {
        let mut history = self.history.write().unwrap();
        let user_history = history.entry(user_id.to_string()).or_insert_with(Vec::new);
        
        user_history.push(message);
        
        // Trim history if too long
        if user_history.len() > MAX_MESSAGES_HISTORY {
            *user_history = user_history.split_off(user_history.len() - MAX_MESSAGES_HISTORY);
        }
    }

    // Get message history for a user
    pub fn get_history(&self, user_id: &str) -> Vec<String> {
        let history = self.history.read().unwrap();
        match history.get(user_id) {
            Some(messages) => messages.clone(),
            None => Vec::new(),
        }
    }
}

// Create a new WebSocket manager
pub fn create_ws_manager() -> WebSocketManager {
    WebSocketManager::new()
}

// Routes setup
pub fn ws_routes() -> Router<AppState> {
    Router::new()
        .route("/chat/:user_id", get(ws_handler))
}

// WebSocket handler
async fn ws_handler(
    ws: WebSocketUpgrade,
    Path(user_id): Path<String>,
    current_user: CurrentUser,
    State(state): State<AppState>,
) -> Result<impl IntoResponse> {
    // Ensure the user is connecting to their own WebSocket
    if current_user.user_id != user_id {
        return Err(AppError::Forbidden("You can only connect to your own WebSocket".to_string()));
    }

    // Check if user exists
    {
        let users = state.users.read().unwrap();
        if !users.contains_key(&user_id) {
            return Err(AppError::NotFound(format!("User with ID {} not found", user_id)));
        }
    }

    // Upgrade the WebSocket connection
    Ok(ws.on_upgrade(move |socket| handle_socket(socket, user_id, state)))
}

// Handle WebSocket connection
async fn handle_socket(socket: WebSocket, user_id: String, state: AppState) {
    let (mut sender, mut receiver) = socket.split();

    // Create a channel for this connection
    let (tx, _rx) = broadcast::channel::<String>(100);
    let connection_id = Uuid::new_v4().to_string();
    
    // Add the connection to the WebSocket manager
    // TODO: Use a real WebSocket manager in AppState
    
    // Send any pending messages from history
    // TODO: Send message history

    // Send an initial status message
    let status_msg = WebSocketMessage::Status {
        user_id: user_id.clone(),
        status: UserStatus::Online,
    };
    
    if let Ok(json) = serde_json::to_string(&status_msg) {
        if let Err(e) = sender.send(Message::Text(json)).await {
            error!("Error sending initial status: {}", e);
            return;
        }
    }

    // Process incoming messages
    while let Some(result) = receiver.next().await {
        match result {
            Ok(message) => {
                match message {
                    Message::Text(text) => {
                        // Parse the message
                        match serde_json::from_str::<WebSocketMessage>(&text) {
                            Ok(ws_message) => {
                                // Process the message
                                match ws_message {
                                    WebSocketMessage::Text { content, .. } => {
                                        // Store the message in the database
                                        // TODO: Store message in database

                                        // Broadcast to the recipient
                                        // TODO: Broadcast to recipient
                                    }
                                    WebSocketMessage::Typing { .. } => {
                                        // Broadcast typing status
                                        // TODO: Broadcast typing status
                                    }
                                    WebSocketMessage::Read { message_ids, .. } => {
                                        // Mark messages as read
                                        // TODO: Mark messages as read
                                    }
                                    WebSocketMessage::Delivered { message_ids, .. } => {
                                        // Mark messages as delivered
                                        // TODO: Mark messages as delivered
                                    }
                                    WebSocketMessage::Status { .. } => {
                                        // Update user status
                                        // TODO: Update user status
                                    }
                                }
                            }
                            Err(e) => {
                                error!("Failed to parse WebSocket message: {}", e);
                            }
                        }
                    }
                    Message::Binary(_) => {
                        // Handle binary messages (like file transfers)
                    }
                    Message::Ping(_) => {
                        // Respond to ping
                        if let Err(e) = sender.send(Message::Pong(Vec::new())).await {
                            error!("Failed to send pong: {}", e);
                            break;
                        }
                    }
                    Message::Pong(_) => {
                        // Handle pong response
                    }
                    Message::Close(_) => {
                        break;
                    }
                }
            }
            Err(e) => {
                error!("Error receiving message: {}", e);
                break;
            }
        }
    }

    // Remove connection when done
    // TODO: Remove connection from WebSocket manager
    
    // Send offline status
    let offline_status = WebSocketMessage::Status {
        user_id: user_id.clone(),
        status: UserStatus::Offline,
    };
    
    // TODO: Broadcast offline status
    
    info!("WebSocket connection closed for user {}", user_id);
} 