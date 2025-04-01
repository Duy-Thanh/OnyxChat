# OnyxChat Server

Secure, end-to-end encrypted messaging server for OnyxChat.

## Features

- End-to-End Encryption (E2EE) for all messages
- JWT-based authentication
- User registration and management
- Secure message storage and delivery
- RESTful API for client integration

## Prerequisites

- Rust (latest stable)
- PostgreSQL
- Docker (optional, for containerized deployment)

## Setup

1. Clone the repository

```bash
git clone https://github.com/yourusername/onyxchat.git
cd onyxchat/server
```

2. Create a `.env` file in the server directory with the following variables:

```bash
DATABASE_URL=postgres://username:password@localhost/onyxchat
JWT_SECRET=your_jwt_secret_key_here
JWT_EXPIRATION=86400  # 24 hours in seconds
PORT=8080
DB_POOL_MAX_SIZE=5
```

3. Set up the database

```bash
# Create a PostgreSQL database
createdb onyxchat
```

4. Build and run the server

```bash
cargo build --release
cargo run --release
```

The server will automatically run migrations on startup.

## API Documentation

The server provides the following API endpoints:

### Authentication

- `POST /v1/auth/register` - Register a new user
- `POST /v1/auth/login` - Log in an existing user
- `POST /v1/auth/refresh` - Refresh an authentication token
- `POST /v1/auth/logout` - Log out a user
- `GET /v1/auth/validate` - Validate a token

### Users

- `GET /v1/users/me` - Get current user information
- `POST /v1/users/me` - Update current user information
- `DELETE /v1/users/me` - Delete current user account
- `GET /v1/users/:user_id` - Get a user's profile
- `GET /v1/users/by-username/:username` - Get a user's profile by username

### Messages

- `POST /v1/messages/` - Send a message
- `GET /v1/messages/` - Get all messages
- `GET /v1/messages/:message_id` - Get a specific message
- `POST /v1/messages/:message_id/received` - Mark a message as received
- `POST /v1/messages/:message_id/read` - Mark a message as read
- `DELETE /v1/messages/:message_id` - Delete a message
- `GET /v1/messages/with/:user_id` - Get messages with a specific user

### Cryptography (E2EE)

- `POST /v1/crypto/prekey-bundle` - Upload a prekey bundle
- `GET /v1/crypto/prekey-bundle/:user_id` - Get a user's prekey bundle

## Docker Deployment

To deploy the server using Docker:

```bash
# Build the Docker image
docker build -t onyxchat-server .

# Run the container
docker run -p 8080:8080 --env-file .env onyxchat-server
```

## Development

For development, you can use cargo-watch to automatically restart the server when changes are made:

```bash
cargo install cargo-watch
cargo watch -x run
```

## License

This project is licensed under the MIT License - see the LICENSE file for details. 