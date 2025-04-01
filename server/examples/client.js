// Simple OnyxChat client example using JavaScript

// Server URL
const SERVER_URL = 'http://localhost:8080';

// Helper function for making API requests
async function apiRequest(endpoint, method = 'GET', data = null, token = null) {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const options = {
    method,
    headers,
    body: data ? JSON.stringify(data) : null,
  };

  const response = await fetch(`${SERVER_URL}${endpoint}`, options);
  const responseData = await response.json();

  if (!response.ok) {
    throw new Error(responseData.error || 'API request failed');
  }

  return responseData;
}

// User registration
async function registerUser(username, email, password) {
  return apiRequest('/api/users', 'POST', { username, email, password });
}

// User login
async function login(username, password) {
  return apiRequest('/api/auth/login', 'POST', { username, password });
}

// Get user by ID
async function getUserById(userId, token) {
  return apiRequest(`/api/users/${userId}`, 'GET', null, token);
}

// Send a message
async function sendMessage(recipientId, content, token) {
  return apiRequest('/api/messages', 'POST', { recipient_id: recipientId, content }, token);
}

// Get messages for user
async function getMessages(userId, token) {
  return apiRequest(`/api/messages/${userId}`, 'GET', null, token);
}

// Register a user key for E2EE
async function registerUserKey(publicKey, token) {
  return apiRequest('/api/crypto/keys', 'POST', { public_key: publicKey }, token);
}

// Register a prekey for E2EE
async function registerPrekey(keyId, publicKey, token) {
  return apiRequest('/api/crypto/prekeys', 'POST', { key_id: keyId, public_key: publicKey }, token);
}

// Get a prekey bundle for a user
async function getPrekeyBundle(userId) {
  return apiRequest(`/api/crypto/prekeys/bundle/${userId}`);
}

// Connect to WebSocket for real-time messaging
function connectWebSocket(userId, token) {
  const ws = new WebSocket(`ws://localhost:8080/api/ws/chat/${userId}`);
  
  // Add token to the WebSocket handshake
  ws.onopen = () => {
    ws.send(JSON.stringify({ type: 'auth', token }));
    console.log('WebSocket connection established');
  };
  
  ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    console.log('Received message:', message);
  };
  
  ws.onerror = (error) => {
    console.error('WebSocket error:', error);
  };
  
  ws.onclose = () => {
    console.log('WebSocket connection closed');
  };
  
  return ws;
}

// Example usage
async function main() {
  try {
    // Register a new user
    console.log('Registering a new user...');
    const user = await registerUser('testuser', 'test@example.com', 'password123');
    console.log('User registered:', user);
    
    // Login
    console.log('Logging in...');
    const auth = await login('testuser', 'password123');
    console.log('Login successful:', auth);
    
    const token = auth.access_token;
    const userId = user.id;
    
    // Get user information
    console.log('Getting user information...');
    const userInfo = await getUserById(userId, token);
    console.log('User info:', userInfo);
    
    // Send a message to self (for demonstration)
    console.log('Sending a message...');
    const message = await sendMessage(userId, 'Hello, this is a test message!', token);
    console.log('Message sent:', message);
    
    // Get messages
    console.log('Getting messages...');
    const messages = await getMessages(userId, token);
    console.log('Messages:', messages);
    
    // Register a user key for E2EE
    console.log('Registering a user key for E2EE...');
    const userKey = await registerUserKey('test_public_key', token);
    console.log('User key registered:', userKey);
    
    // Register a prekey for E2EE
    console.log('Registering a prekey for E2EE...');
    const prekey = await registerPrekey('prekey1', 'test_prekey_value', token);
    console.log('Prekey registered:', prekey);
    
    // Get a prekey bundle
    console.log('Getting a prekey bundle...');
    const prekeyBundle = await getPrekeyBundle(userId);
    console.log('Prekey bundle:', prekeyBundle);
    
    // Connect to WebSocket (commented out for demonstration)
    // const ws = connectWebSocket(userId, token);
    
    console.log('All operations completed successfully!');
  } catch (error) {
    console.error('Error:', error.message);
  }
}

// Run the example
main(); 