#!/usr/bin/env python3
"""
Simple OnyxChat client example using Python and the requests library.
"""

import json
import requests
import websocket
import threading
import time

# Server URL
SERVER_URL = 'http://localhost:8080'

def api_request(endpoint, method='GET', data=None, token=None):
    """Make an API request to the OnyxChat server."""
    headers = {
        'Content-Type': 'application/json'
    }
    
    if token:
        headers['Authorization'] = f'Bearer {token}'
    
    url = f"{SERVER_URL}{endpoint}"
    
    if method == 'GET':
        response = requests.get(url, headers=headers)
    elif method == 'POST':
        response = requests.post(url, headers=headers, json=data)
    elif method == 'PUT':
        response = requests.put(url, headers=headers, json=data)
    elif method == 'DELETE':
        response = requests.delete(url, headers=headers)
    else:
        raise ValueError(f"Unsupported HTTP method: {method}")
    
    if response.status_code >= 400:
        try:
            error_data = response.json()
            error_message = error_data.get('error', 'Unknown error')
        except:
            error_message = response.text or f"Request failed with status code {response.status_code}"
        raise Exception(error_message)
    
    return response.json() if response.text else None

def register_user(username, email, password):
    """Register a new user."""
    return api_request('/api/users', 'POST', {
        'username': username,
        'email': email,
        'password': password
    })

def login(username, password):
    """Login an existing user."""
    return api_request('/api/auth/login', 'POST', {
        'username': username,
        'password': password
    })

def get_user_by_id(user_id, token):
    """Get a user by ID."""
    return api_request(f'/api/users/{user_id}', token=token)

def send_message(recipient_id, content, token):
    """Send a message."""
    return api_request('/api/messages', 'POST', {
        'recipient_id': recipient_id,
        'content': content
    }, token)

def get_messages(user_id, token):
    """Get messages for a user."""
    return api_request(f'/api/messages/{user_id}', token=token)

def register_user_key(public_key, token):
    """Register a user key for E2EE."""
    return api_request('/api/crypto/keys', 'POST', {
        'public_key': public_key
    }, token)

def register_prekey(key_id, public_key, token):
    """Register a prekey for E2EE."""
    return api_request('/api/crypto/prekeys', 'POST', {
        'key_id': key_id,
        'public_key': public_key
    }, token)

def get_prekey_bundle(user_id):
    """Get a prekey bundle for a user."""
    return api_request(f'/api/crypto/prekeys/bundle/{user_id}')

def on_message(ws, message):
    """Handle incoming WebSocket messages."""
    data = json.loads(message)
    print(f"Received message: {data}")

def on_error(ws, error):
    """Handle WebSocket errors."""
    print(f"WebSocket error: {error}")

def on_close(ws, close_status_code, close_msg):
    """Handle WebSocket connection close."""
    print("WebSocket connection closed")

def on_open(ws):
    """Handle WebSocket connection open."""
    print("WebSocket connection opened")
    # You could send an authentication message here if needed
    # ws.send(json.dumps({'type': 'auth', 'token': token}))

def connect_websocket(user_id, token):
    """Connect to the WebSocket for real-time messaging."""
    ws_url = f"ws://localhost:8080/api/ws/chat/{user_id}"
    ws = websocket.WebSocketApp(
        ws_url,
        on_message=on_message,
        on_error=on_error,
        on_close=on_close
    )
    ws.on_open = on_open
    
    # Add authorization header for WebSocket handshake
    ws.header = {"Authorization": f"Bearer {token}"}
    
    # Start the WebSocket connection in a separate thread
    wst = threading.Thread(target=ws.run_forever)
    wst.daemon = True
    wst.start()
    
    return ws

def main():
    """Main function to demonstrate the API usage."""
    try:
        # Register a new user
        print("Registering a new user...")
        user = register_user('pythonuser', 'python@example.com', 'password123')
        print(f"User registered: {user}")
        
        # Login
        print("Logging in...")
        auth = login('pythonuser', 'password123')
        print(f"Login successful: {auth}")
        
        token = auth['access_token']
        user_id = user['id']
        
        # Get user information
        print("Getting user information...")
        user_info = get_user_by_id(user_id, token)
        print(f"User info: {user_info}")
        
        # Send a message to self (for demonstration)
        print("Sending a message...")
        message = send_message(user_id, "Hello from Python client!", token)
        print(f"Message sent: {message}")
        
        # Get messages
        print("Getting messages...")
        messages = get_messages(user_id, token)
        print(f"Messages: {messages}")
        
        # Register a user key for E2EE
        print("Registering a user key for E2EE...")
        user_key = register_user_key("python_test_public_key", token)
        print(f"User key registered: {user_key}")
        
        # Register a prekey for E2EE
        print("Registering a prekey for E2EE...")
        prekey = register_prekey("pythonprekey1", "python_test_prekey_value", token)
        print(f"Prekey registered: {prekey}")
        
        # Get a prekey bundle
        print("Getting a prekey bundle...")
        prekey_bundle = get_prekey_bundle(user_id)
        print(f"Prekey bundle: {prekey_bundle}")
        
        # Connect to WebSocket (uncomment for real-time messaging)
        # ws = connect_websocket(user_id, token)
        # time.sleep(10)  # Keep the connection open for 10 seconds
        
        print("All operations completed successfully!")
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main() 