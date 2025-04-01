#!/usr/bin/env python3
import websocket
import time
import sys

# Connect to the WebSocket server
def test_websocket(user_id):
    ws_url = f"ws://localhost:8081/ws/{user_id}"
    print(f"Connecting to {ws_url}...")
    
    try:
        # Create the WebSocket connection
        ws = websocket.create_connection(ws_url)
        print("Connected!")
        
        # Get welcome message
        result = ws.recv()
        print(f"Received: {result}")
        
        # Send some test messages
        for i in range(3):
            message = f"Test message {i+1}"
            print(f"Sending: {message}")
            ws.send(message)
            
            # Get the response
            result = ws.recv()
            print(f"Received: {result}")
            time.sleep(1)
        
        # Close the connection
        ws.close()
        print("Connection closed")
        return True
    except Exception as e:
        print(f"Error: {e}")
        return False

if __name__ == "__main__":
    # Use a provided user ID or a default one
    user_id = sys.argv[1] if len(sys.argv) > 1 else "test-user"
    test_websocket(user_id) 