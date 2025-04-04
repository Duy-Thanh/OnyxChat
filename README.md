# OnyxChat

An end-to-end encrypted messaging application with a focus on privacy and security.

## Features

- End-to-end encryption for all messages
- Real-time messaging using WebSockets
- User authentication and account management
- Contact management
- Android mobile client

## Setup Instructions

### Prerequisites

- Docker and Docker Compose
- Android Studio (for mobile development)
- Node.js v18+ (for local development without Docker)

### Quick Start with Docker

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/onyxchat.git
   cd onyxchat
   ```

2. Run the initialization script:
   ```bash
   chmod +x init-setup.sh
   ./init-setup.sh
   ```

This will:
- Start PostgreSQL database in a Docker container
- Build and start the Node.js server
- Initialize the database with sample users

### Server Access

- API Endpoint: http://localhost:8082
- WebSocket Endpoint: ws://localhost:8082/ws

### Default Users

After initialization, the following users are available:

| Username | Password | Role  |
|----------|----------|-------|
| admin    | admin123 | Admin |
| user     | admin123 | User  |
| testuser | admin123 | User  |

### Android Development

1. Open the project in Android Studio
2. Connect to the emulator using `10.0.2.2:8082` as the server address
3. Use the default users to log in

## Production Deployment

### Prerequisites for Production

- A Linux server with Docker and Docker Compose installed
- Domain name pointed to your server (for HTTPS)
- At least 2GB RAM and 1 CPU core

### Production Setup

1. Clone the repository on your production server:
   ```bash
   git clone https://github.com/yourusername/onyxchat.git
   cd onyxchat
   ```

2. Create SSL certificates for HTTPS:
   ```bash
   mkdir -p certs
   # For production with Let's Encrypt:
   # sudo certbot certonly --standalone -d yourdomain.com
   # cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem certs/server.crt
   # cp /etc/letsencrypt/live/yourdomain.com/privkey.pem certs/server.key
   
   # For testing, you can create self-signed certificates:
   openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout certs/server.key -out certs/server.crt
   ```

3. Prepare the production environment file:
   ```bash
   cp .env.production.example .env.production
   # Edit the file to set secure passwords and secrets
   nano .env.production
   ```

4. Run the production setup script:
   ```bash
   chmod +x production-setup.sh
   sudo ./production-setup.sh
   ```

5. Access your application at:
   - API: https://yourdomain.com
   - WebSocket: wss://yourdomain.com/ws

### Production Configuration Options

The following environment variables can be configured in `.env.production`:

| Variable | Description | Default |
|----------|-------------|---------|
| DB_USER | Database username | postgres |
| DB_PASSWORD | Database password | postgres (change this!) |
| JWT_SECRET | Secret for JWT tokens | (generated) |
| JWT_REFRESH_SECRET | Secret for refresh tokens | (generated) |
| ENCRYPTION_KEY | Key for message encryption | (generated) |
| ENABLE_HTTPS | Whether to enable HTTPS | false |
| SERVER_REPLICAS | Number of server instances | 1 |
| REDIS_PASSWORD | Password for Redis | redis |

### Backup and Maintenance

- Database backups are automatically created daily at 3:00 AM
- Backup files are stored in the `backups` directory
- Backups older than 7 days are automatically deleted

To manually trigger a backup:
```bash
docker exec onyxchat-postgres pg_dump -U postgres onyxchat > backup-manual.sql
```

### Scaling for Higher Load

To scale the application for higher load:

1. Increase server replicas in `.env.production`:
   ```
   SERVER_REPLICAS=3
   ```

2. Restart the services:
   ```bash
   sudo docker-compose -f docker-compose.prod.yml up -d
   ```

## Development

### Manual Setup (Without Docker)

1. Configure PostgreSQL:
   - Create a database named `onyxchat`
   - Run the migration: `cd nodejs-server && node src/db/migrate.js`
   - Run the seed script: `node src/db/seed.js`

2. Start the Node.js server:
   ```bash
   cd nodejs-server
   npm install
   npm start
   ```

### API Documentation

The API follows RESTful conventions:

- Authentication: `/api/auth/login` and `/api/auth/register`
- Users: `/api/users`
- Messages: `/api/messages`
- Contacts: `/api/contacts`

# Developers:

nekkochan0x0007 (Nguyen Duy Thanh) and the OnyxChat team