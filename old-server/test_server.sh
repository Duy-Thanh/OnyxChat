#!/bin/bash

SERVER_URL="http://localhost:8080"

echo "Testing OnyxChat Server API..."
echo "---------------------------------"

# Test the hello world endpoint
echo "1. Testing Hello World endpoint:"
curl -s $SERVER_URL/
echo -e "\n"

# Test health check
echo "2. Testing Health Check endpoint:"
curl -s $SERVER_URL/health
echo -e "\n"

# Create a user
echo "3. Creating a new user:"
USER_RESPONSE=$(curl -s -X POST $SERVER_URL/api/users \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "email": "test@example.com", "password": "password123"}')

echo $USER_RESPONSE
USER_ID=$(echo $USER_RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "User ID: $USER_ID"
echo -e "\n"

# Log in with the user
echo "4. Logging in with the created user:"
AUTH_RESPONSE=$(curl -s -X POST $SERVER_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "password123"}')

echo $AUTH_RESPONSE
TOKEN=$(echo $AUTH_RESPONSE | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
echo "Token: $TOKEN"
echo -e "\n"

# Get user by ID
echo "5. Getting user by ID:"
curl -s $SERVER_URL/api/users/$USER_ID
echo -e "\n"

# Send a message (protected route)
echo "6. Sending a message (requires authentication):"
curl -s -X POST $SERVER_URL/api/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"recipient_id\": \"$USER_ID\", \"content\": \"Hello from test script!\"}"
echo -e "\n"

# Get messages for user (protected route)
echo "7. Getting messages for user (requires authentication):"
curl -s $SERVER_URL/api/messages/$USER_ID \
  -H "Authorization: Bearer $TOKEN"
echo -e "\n"

# Test E2EE functionality
echo "8. Testing E2EE functionality:"

# Register a user key
echo "8.1. Registering a user key:"
USER_KEY_RESPONSE=$(curl -s -X POST $SERVER_URL/api/crypto/keys \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"public_key": "test_public_key_value"}')

echo $USER_KEY_RESPONSE
echo -e "\n"

# Get user keys
echo "8.2. Getting user keys:"
curl -s $SERVER_URL/api/crypto/keys/$USER_ID
echo -e "\n"

# Register a prekey
echo "8.3. Registering a prekey:"
PREKEY_RESPONSE=$(curl -s -X POST $SERVER_URL/api/crypto/prekeys \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"key_id": "prekey1", "public_key": "test_prekey_value"}')

echo $PREKEY_RESPONSE
echo -e "\n"

# Get a prekey bundle
echo "8.4. Getting a prekey bundle:"
curl -s $SERVER_URL/api/crypto/prekeys/bundle/$USER_ID
echo -e "\n"

echo "---------------------------------"
echo "End of test." 