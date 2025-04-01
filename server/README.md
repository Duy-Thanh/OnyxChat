# OnyxChat Server

A secure messaging server with End-to-End Encryption (E2EE) support, built with Rust and Axum.

## Features

- RESTful API for user and message management
- JWT-based authentication with secure password hashing (Argon2)
- End-to-End Encryption support with prekey bundles
- WebSocket support for real-time messaging
- Comprehensive error handling
- CORS support for web clients

## Getting Started

### Prerequisites

- Rust (latest stable version)
- Cargo

### Installation

1. Clone the repository
```bash
git clone https://github.com/yourusername/onyxchat.git
cd onyxchat/server
```

2. Build the server
```bash
cargo build --release
```

3. Run the server
```bash
cargo run --release
```

The server will start on `0.0.0.0:8080` by default.

## API Reference

### Authentication

#### Register a new user
```
POST /api/users
Content-Type: application/json

{
  "username": "user1",
  "email": "user1@example.com",
  "password": "securepassword"
}
```

#### Login
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "user1",
  "password": "securepassword"
}
```

Response:
```json
{
  "access_token": "jwt_token_here",
  "token_type": "Bearer",
  "expires_in": 86400
}
```

### Users

#### Get user by ID
```
GET /api/users/:id
```

### Messages

#### Send a message
```
POST /api/messages
Authorization: Bearer {token}
Content-Type: application/json

{
  "recipient_id": "user_id_here",
  "content": "Hello, this is a message!"
}
```

#### Get messages for user
```
GET /api/messages/:user_id
Authorization: Bearer {token}
```

### End-to-End Encryption (E2EE)

#### Register a user's identity key
```
POST /api/crypto/keys
Authorization: Bearer {token}
Content-Type: application/json

{
  "public_key": "base64_encoded_public_key"
}
```

#### Get user keys
```
GET /api/crypto/keys/:user_id
```

#### Register a one-time prekey
```
POST /api/crypto/prekeys
Authorization: Bearer {token}
Content-Type: application/json

{
  "key_id": "key_id",
  "public_key": "base64_encoded_public_key"
}
```

#### Get a prekey bundle for a user
```
GET /api/crypto/prekeys/bundle/:user_id
```

### WebSockets

#### Connect to WebSocket
```
GET /api/ws/chat/:user_id
Authorization: Bearer {token}
```

## Testing

A test script is included in the repository to test the basic API functionality:

```bash
./test_server.sh
```

## Architecture

The server is built with a modular architecture:

- `main.rs`: Server setup and route definitions
- `auth.rs`: Authentication logic and JWT handling
- `error.rs`: Error handling and responses
- `crypto.rs`: E2EE functionality with identity keys and prekeys
- `ws.rs`: WebSocket handling for real-time communication

## Security Considerations

- Passwords are hashed using Argon2, a modern password-hashing function
- JWT tokens are used for authentication with a 24-hour expiration
- All API endpoints that access or modify user data are protected
- End-to-End Encryption support ensures messages can only be read by intended recipients

## Future Improvements

- Database persistence using PostgreSQL
- Rate limiting for API endpoints
- Email verification
- Group chats
- File transfers
- Push notifications
- Admin functionality
- Metrics and monitoring

## License

This project is licensed under the MIT License - see the LICENSE file for details. 