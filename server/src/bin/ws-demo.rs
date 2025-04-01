use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        Path, State,
    },
    response::IntoResponse,
    routing::get,
    Router,
};
use futures::{sink::SinkExt, stream::StreamExt};
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    net::SocketAddr,
    sync::{Arc, RwLock},
};
use tokio::sync::mpsc;
use tracing::info;
use uuid::Uuid;

// Simple in-memory user and connection storage
#[derive(Clone, Debug)]
struct AppState {
    connections: Arc<RwLock<HashMap<String, HashMap<String, mpsc::UnboundedSender<Message>>>>>,
    users: Arc<RwLock<HashMap<String, User>>>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
struct User {
    id: String,
    username: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
struct ChatMessage {
    id: String,
    sender_id: String,
    recipient_id: String,
    content: String,
    timestamp: u64,
}

// WebSocket message types
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(tag = "type", content = "data")]
enum WsMessage {
    Text(ChatMessage),
    Typing { user_id: String, is_typing: bool },
    Status { user_id: String, status: String },
    Connect { user_id: String, username: String },
}

#[tokio::main]
async fn main() {
    // Setup tracing
    tracing_subscriber::fmt::init();

    // Create app state
    let app_state = AppState {
        connections: Arc::new(RwLock::new(HashMap::new())),
        users: Arc::new(RwLock::new(HashMap::new())),
    };

    // Build the router
    let app = Router::new()
        .route("/", get(|| async { "WebSocket Demo Server" }))
        .route("/ws/:user_id", get(ws_handler))
        .with_state(app_state);

    // Run the server
    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    info!("Listening on {}", addr);
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();
}

// Handle WebSocket connections
async fn ws_handler(
    ws: WebSocketUpgrade,
    Path(user_id): Path<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    info!("WebSocket upgrade requested for user: {}", user_id);
    
    // Simulate connecting a user or getting an existing one
    let username = format!("User_{}", &user_id[..6]);
    let user = User {
        id: user_id.clone(),
        username: username.clone(),
    };
    
    // Store user in the state
    {
        let mut users = state.users.write().unwrap();
        users.insert(user_id.clone(), user.clone());
    }

    ws.on_upgrade(move |socket| handle_socket(socket, user_id, user, state))
}

// The actual WebSocket handler
async fn handle_socket(socket: WebSocket, user_id: String, user: User, state: AppState) {
    info!("WebSocket connection established for user: {}", &user_id);
    
    // Create a channel for this connection to send messages to the WebSocket
    let (to_ws_tx, to_ws_rx) = mpsc::unbounded_channel();
    
    // Generate a unique connection ID
    let connection_id = Uuid::new_v4().to_string();
    
    // Store the connection
    {
        let mut connections = state.connections.write().unwrap();
        let user_connections = connections.entry(user_id.clone()).or_insert_with(HashMap::new);
        user_connections.insert(connection_id.clone(), to_ws_tx.clone());
    }
    
    // Send a welcome message
    let welcome_msg = serde_json::to_string(&WsMessage::Text(ChatMessage {
        id: Uuid::new_v4().to_string(),
        sender_id: "system".to_string(),
        recipient_id: user_id.clone(),
        content: format!("Welcome, {}!", user.username),
        timestamp: current_timestamp(),
    })).unwrap();
    
    let _ = to_ws_tx.send(Message::Text(welcome_msg));
    
    // Broadcast that this user is now online
    broadcast_user_status(&state, &user_id, "online");
    
    // Process the WebSocket connection
    process_socket(socket, user_id, connection_id, to_ws_rx, to_ws_tx, state).await;
}

// Function to process the WebSocket connection
async fn process_socket(
    socket: WebSocket,
    user_id: String,
    connection_id: String,
    mut to_ws_rx: mpsc::UnboundedReceiver<Message>,
    to_ws_tx: mpsc::UnboundedSender<Message>,
    state: AppState,
) {
    // Split the socket
    let (mut ws_sender, mut ws_receiver) = socket.split();
    
    // Handle outgoing messages (server to client)
    let mut send_task = tokio::task::spawn(async move {
        while let Some(message) = to_ws_rx.recv().await {
            if ws_sender.send(message).await.is_err() {
                break;
            }
        }
    });
    
    // Handle incoming messages (client to server)
    let state_clone = state.clone();
    let user_id_clone = user_id.clone();
    let mut recv_task = tokio::task::spawn(async move {
        while let Some(result) = ws_receiver.next().await {
            match result {
                Ok(Message::Text(text)) => {
                    info!("Received message from {}: {}", &user_id_clone, text);
                    
                    // Try to parse as a WebSocket message
                    if let Ok(ws_message) = serde_json::from_str::<WsMessage>(&text) {
                        match ws_message.clone() {
                            WsMessage::Text(chat_message) => {
                                // Broadcast the message to the recipient
                                let recipient_id = chat_message.recipient_id.clone();
                                let message_json = serde_json::to_string(&ws_message).unwrap();
                                
                                // Send to recipient
                                send_to_user(&state_clone.connections, &recipient_id, &message_json);
                                
                                // Echo the message back to confirm it was sent
                                let echo_message = WsMessage::Text(ChatMessage {
                                    id: chat_message.id,
                                    sender_id: chat_message.sender_id,
                                    recipient_id: chat_message.recipient_id,
                                    content: chat_message.content,
                                    timestamp: chat_message.timestamp,
                                });
                                
                                let echo_json = serde_json::to_string(&echo_message).unwrap();
                                let _ = to_ws_tx.send(Message::Text(echo_json));
                            },
                            WsMessage::Typing { user_id: _, is_typing: _ } => {
                                // Broadcast typing status to all connected clients
                                let typing_json = serde_json::to_string(&ws_message).unwrap();
                                broadcast_message(&state_clone.connections, &typing_json);
                            },
                            WsMessage::Status { .. } => {
                                // Ignore status messages from clients
                            },
                            WsMessage::Connect { .. } => {
                                // Ignore connect messages from clients
                            }
                        }
                    } else {
                        info!("Error parsing message: {}", text);
                        
                        // Send a simple echo back
                        let _ = to_ws_tx.send(Message::Text(format!("Echo: {}", text)));
                    }
                },
                Ok(Message::Binary(data)) => {
                    // Echo binary data back
                    let _ = to_ws_tx.send(Message::Binary(data));
                },
                Ok(Message::Close(_)) => {
                    info!("WebSocket closed by client");
                    break;
                },
                _ => {
                    // Ignore other message types
                }
            }
        }
    });
    
    // Wait for either task to complete
    tokio::select! {
        _ = &mut send_task => recv_task.abort(),
        _ = &mut recv_task => send_task.abort(),
    }
    
    // Connection closed, cleanup
    info!("WebSocket connection closed for user: {}", &user_id);
    
    // Remove the connection
    remove_connection(&state.connections, &user_id, &connection_id);
    
    // If this was the last connection for this user, broadcast that they are offline
    if state.connections.read().unwrap().get(&user_id).map_or(true, |conns| conns.is_empty()) {
        broadcast_user_status(&state, &user_id, "offline");
    }
}

// Helper function to remove a connection
fn remove_connection(
    connections: &Arc<RwLock<HashMap<String, HashMap<String, mpsc::UnboundedSender<Message>>>>>,
    user_id: &str,
    connection_id: &str,
) {
    let mut connections = connections.write().unwrap();
    if let Some(user_connections) = connections.get_mut(user_id) {
        user_connections.remove(connection_id);
        if user_connections.is_empty() {
            connections.remove(user_id);
        }
    }
}

// Helper function to broadcast user status
fn broadcast_user_status(state: &AppState, user_id: &str, status: &str) {
    let status_message = WsMessage::Status {
        user_id: user_id.to_string(),
        status: status.to_string(),
    };
    let status_json = serde_json::to_string(&status_message).unwrap();
    
    // Broadcast to all connected clients
    broadcast_message(&state.connections, &status_json);
}

// Helper function to send a message to a specific user
fn send_to_user(
    connections: &Arc<RwLock<HashMap<String, HashMap<String, mpsc::UnboundedSender<Message>>>>>,
    user_id: &str,
    message: &str,
) {
    if let Some(user_connections) = connections.read().unwrap().get(user_id) {
        for (_, tx) in user_connections.iter() {
            let _ = tx.send(Message::Text(message.to_string()));
        }
    }
}

// Helper function to broadcast a message to all users
fn broadcast_message(
    connections: &Arc<RwLock<HashMap<String, HashMap<String, mpsc::UnboundedSender<Message>>>>>,
    message: &str,
) {
    for (_, user_connections) in connections.read().unwrap().iter() {
        for (_, tx) in user_connections.iter() {
            let _ = tx.send(Message::Text(message.to_string()));
        }
    }
}

// Helper function to get current timestamp
fn current_timestamp() -> u64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs()
} 