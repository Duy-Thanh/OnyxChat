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
    Message as DbMessage,
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
#[derive(Clone)]
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

    // Broadcast user status to all connected users who have this user in their contacts
    // In a real app, we'd have a contacts table to determine who should receive status updates
    pub fn broadcast_status(&self, user_id: &str, status: UserStatus) {
        let status_msg = WebSocketMessage::Status {
            user_id: user_id.to_string(),
            status,
        };
        
        if let Ok(json) = serde_json::to_string(&status_msg) {
            // For simplicity, we're just broadcasting to the user themselves in this example
            // In a real app, you'd broadcast to all users who have this user in their contacts
            self.broadcast_to_user(user_id, json);
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
    // Parse the user ID - Clone it to use in multiple tasks
    let uid = user_id.clone();

    // Generate a unique connection ID
    let connection_id = Uuid::new_v4().to_string();
    let connection_id_clone = connection_id.clone();

    // Split the socket
    let (mut sender, mut receiver) = socket.split();

    // Create a broadcast channel for this connection
    let (tx, _rx) = broadcast::channel::<String>(100);
    
    // Add connection to the manager
    state.ws_manager.add_connection(&uid, &connection_id, tx.clone());
    
    // Broadcast user status to all connections
    state.ws_manager.broadcast_status(&uid, UserStatus::Online);

    // Send message history
    let message_history = state.ws_manager.get_history(&uid);
    for message in message_history {
        if let Err(e) = sender.send(Message::Text(message)).await {
            error!("Error sending message history: {}", e);
        }
    }

    // Clone references to use in the tasks
    let ws_manager = Arc::clone(&state.ws_manager);
    let uid_clone = uid.clone();
    let conn_id = connection_id.clone();

    // Spawn a task to receive messages from the client
    let mut recv_task = tokio::spawn(async move {
        while let Some(result) = receiver.next().await {
            match result {
                Ok(msg) => {
                    match msg {
                        Message::Text(text) => {
                            // Parse the message
                            if let Ok(ws_message) = serde_json::from_str::<WebSocketMessage>(&text) {
                                match ws_message {
                                    WebSocketMessage::Text { content, sender_id } => {
                                        // Handle text message
                                        info!("Received text message from {}: {}", sender_id, content);
                                        
                                        // Create a message format that can be broadcasted
                                        let message_json = serde_json::json!({
                                            "type": "text",
                                            "content": content,
                                            "sender_id": sender_id,
                                            "timestamp": chrono::Utc::now().to_rfc3339()
                                        });
                                        
                                        if let Ok(json) = serde_json::to_string(&message_json) {
                                            // Broadcast to recipient - this has to be fixed for your specific logic
                                            let recipient_id = uid_clone.clone(); // Example: broadcast to self for now
                                            ws_manager.broadcast_to_user(&recipient_id, json);
                                        }
                                    },
                                    WebSocketMessage::Typing { is_typing, user_id: ref typing_user_id } => {
                                        // Note the 'ref' keyword to prevent moving the String
                                        info!("User {} typing status: {}", typing_user_id, is_typing);
                                        
                                        // Create typing message that can be broadcasted
                                        let typing_message = serde_json::json!({
                                            "type": "typing",
                                            "is_typing": is_typing,
                                            "user_id": typing_user_id
                                        });
                                        
                                        if let Ok(json) = serde_json::to_string(&typing_message) {
                                            // Broadcast typing status
                                            ws_manager.broadcast_to_user(typing_user_id, json);
                                        }
                                    },
                                    WebSocketMessage::Read { message_ids, user_id: ref reader_id } => {
                                        // Note the 'ref' keyword to prevent moving the String
                                        info!("User {} read messages: {:?}", reader_id, message_ids);
                                        
                                        // You'd typically update your database here to mark messages as read
                                        // ...
                                        
                                        // And notify the sender
                                        let read_message = serde_json::json!({
                                            "type": "read",
                                            "message_ids": message_ids,
                                            "user_id": reader_id
                                        });
                                        
                                        if let Ok(json) = serde_json::to_string(&read_message) {
                                            // This would actually go to the message sender, but for simplicity:
                                            ws_manager.broadcast_to_user(reader_id, json);
                                        }
                                    },
                                    WebSocketMessage::Delivered { message_ids, user_id: ref recipient_id } => {
                                        // Note the 'ref' keyword to prevent moving the String
                                        info!("User {} received messages: {:?}", recipient_id, message_ids);
                                        
                                        // You'd typically update your database here to mark messages as delivered
                                        // ...
                                        
                                        // And notify the sender
                                        let delivered_message = serde_json::json!({
                                            "type": "delivered",
                                            "message_ids": message_ids,
                                            "user_id": recipient_id
                                        });
                                        
                                        if let Ok(json) = serde_json::to_string(&delivered_message) {
                                            // This would actually go to the message sender, but for simplicity:
                                            ws_manager.broadcast_to_user(recipient_id, json);
                                        }
                                    },
                                    WebSocketMessage::Status { user_id: ref status_user_id, status } => {
                                        // Note the 'ref' keyword to prevent moving the String
                                        info!("User {} status updated: {:?}", status_user_id, status);
                                        
                                        // Broadcast status update
                                        ws_manager.broadcast_status(status_user_id, status);
                                    }
                                }
                            } else {
                                error!("Failed to parse message: {}", text);
                            }
                        },
                        Message::Binary(_) => {
                            // Handle binary message if needed
                        },
                        Message::Ping(ping) => {
                            if let Err(e) = sender.send(Message::Pong(ping)).await {
                                error!("Error sending pong: {}", e);
                            }
                        },
                        Message::Pong(_) => {},
                        Message::Close(_) => break,
                    }
                },
                Err(e) => {
                    error!("Error receiving message: {}", e);
                    break;
                }
            }
        }
        
        // Return connection ID to be used for cleanup
        conn_id
    });

    // Wait for receive task to complete
    match recv_task.await {
        Ok(connection_id) => {
            // Use the cloned state.ws_manager to avoid the move issue
            let uid = uid.clone(); // Use the cloned uid to avoid the move issue
            ws_manager.remove_connection(&uid, &connection_id);
            ws_manager.broadcast_status(&uid, UserStatus::Offline);
            info!("WebSocket connection closed for user {}", uid);
        },
        Err(e) => {
            error!("Error in WebSocket task: {}", e);
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