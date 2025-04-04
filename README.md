# OnyxChat

Secure messaging application with end-to-end encryption.

## Docker Setup (Recommended)

### Requirements
- Docker
- Docker Compose

### Starting the Application

```bash
# From the project root
sudo docker-compose up -d
```

This will start both the PostgreSQL database and the Node.js server in Docker containers. The server will be available at http://localhost:8081.

### Stopping the Application

```bash
# From the project root
sudo docker-compose down
```

### Viewing Logs

```bash
# View logs for all services
sudo docker-compose logs

# View logs for a specific service
sudo docker-compose logs server
sudo docker-compose logs postgres

# Follow logs (stream)
sudo docker-compose logs -f
```

### Rebuilding the Application

```bash
# Rebuild and restart
sudo docker-compose up -d --build
```

### Completely Reset the Database

```bash
# Stop the containers
sudo docker-compose down

# Remove the volume
sudo docker volume rm onyxchat_postgres_data

# Start everything again
sudo docker-compose up -d
```

## Local Development Setup

If you prefer to run the server outside Docker:

### Requirements
- Node.js (v18+)
- PostgreSQL

### Setup

1. Install dependencies:
```bash
cd nodejs-server
npm install
```

2. Configure environment variables (modify `.env` file)

3. Initialize the database:
```bash
npm run db:migrate
npm run db:seed
```

4. Start the server:
```bash
# Production mode
npm run start

# Development mode with auto-reload
npm run dev

# Mock database mode (no PostgreSQL required)
npm run mock
```

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login and get access token
- `GET /api/auth/me` - Get current user profile
- `POST /api/auth/refresh` - Refresh access token

### Users
- `GET /api/users` - Get all users
- `GET /api/users/:id` - Get user by ID

### Messages
- `GET /api/messages` - Get all messages for current user
- `GET /api/messages/:id` - Get message by ID
- `GET /api/messages/with/:userId` - Get conversation with specific user
- `POST /api/messages` - Send a message
- `PUT /api/messages/:id/received` - Mark message as received
- `PUT /api/messages/:id/read` - Mark message as read
- `DELETE /api/messages/:id` - Delete a message

## Test Users

The following users are created by default:

1. Admin User
   - Username: `admin`
   - Password: `password`

2. Regular User
   - Username: `user`
   - Password: `password` 