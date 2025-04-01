#!/usr/bin/env python3
import websocket
import time
import threading
import json
import sys

# Store active connections
connections = {}

# Handler for incoming messages
def on_message(ws, message):
    user_id = ws.user_id
    print(f"[{user_id}] Received: {message}")

# Handler for connection open
def on_open(ws):
    user_id = ws.user_id
    print(f"[{user_id}] Connected to server")
    connections[user_id] = ws

# Handler for connection close
def on_close(ws, close_status_code, close_msg):
    user_id = ws.user_id
    print(f"[{user_id}] Connection closed")
    if user_id in connections:
        del connections[user_id]

# Handler for errors
def on_error(ws, error):
    user_id = ws.user_id
    print(f"[{user_id}] Error: {error}")

# Create a WebSocket connection for a user
def create_connection(user_id):
    ws_url = f"ws://localhost:8081/ws/{user_id}"
    print(f"Connecting {user_id} to {ws_url}...")
    
    ws = websocket.WebSocketApp(
        ws_url,
        on_message=on_message,
        on_open=on_open,
        on_close=on_close,
        on_error=on_error
    )
    
    # Store user_id in the WebSocket object
    ws.user_id = user_id
    
    # Start the WebSocket connection in a thread
    thread = threading.Thread(target=ws.run_forever)
    thread.daemon = True
    thread.start()
    
    # Wait for connection to establish
    time.sleep(1)
    return ws

# Send a direct message
def send_direct_message(from_user, to_user, message):
    if from_user not in connections:
        print(f"Error: {from_user} is not connected")
        return False
        
    ws = connections[from_user]
    formatted_message = f"@{to_user}:{message}"
    print(f"[{from_user}] Sending DM to {to_user}: {message}")
    ws.send(formatted_message)
    return True

# Send a regular message
def send_message(from_user, message):
    if from_user not in connections:
        print(f"Error: {from_user} is not connected")
        return False
        
    ws = connections[from_user]
    print(f"[{from_user}] Sending message: {message}")
    ws.send(message)
    return True

# Run the test
def run_direct_message_test():
    # Create connections for multiple users
    users = ["user1", "user2", "user3"]
    
    # Connect all users
    for user in users:
        create_connection(user)
    
    # Wait for all connections to establish
    time.sleep(2)
    
    # Check which users are connected
    print(f"Connected users: {list(connections.keys())}")
    
    # Send some direct messages
    if len(connections) >= 2:
        time.sleep(1)
        send_direct_message("user1", "user2", "Hello user2 from user1!")
        time.sleep(1)
        send_direct_message("user2", "user1", "Hi user1, got your message!")
        time.sleep(1)
        send_direct_message("user1", "user3", "Hey user3, join our conversation!")
        time.sleep(1)
        send_direct_message("user3", "user1", "I'm here user1!")
        time.sleep(1)
        send_direct_message("user3", "user2", "Hello user2, user3 here!")
        time.sleep(1)
        
        # Send a regular message
        send_message("user1", "This is a regular broadcast message")
        time.sleep(1)
        
        # Try sending to a non-existent user
        send_direct_message("user1", "nonexistent", "Will this get delivered?")
        time.sleep(2)
    
    # Keep the connections open for a while
    print("Test completed. Keeping connections open for 5 more seconds...")
    time.sleep(5)
    
    # Close all connections
    for user_id, ws in list(connections.items()):
        print(f"Closing connection for {user_id}")
        ws.close()
    
    time.sleep(1)
    print("All connections closed.")

if __name__ == "__main__":
    websocket.enableTrace(False)  # Set to True for detailed WebSocket logs
    run_direct_message_test() 