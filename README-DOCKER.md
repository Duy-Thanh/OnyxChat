# OnyxChat Docker Setup

This guide explains how to run the OnyxChat server and PostgreSQL database using Docker.

## Prerequisites

- Docker
- Docker Compose

## Getting Started

1. Clone the repository:
   ```
   git clone https://github.com/yourusername/onyxchat.git
   cd onyxchat
   ```

2. Start the containers:
   ```
   sudo docker-compose up -d
   ```

   This will:
   - Start a PostgreSQL database container
   - Build and start the OnyxChat server container
   - Run database migrations and seed data
   - Make the server available on port 8081

3. Check if the containers are running:
   ```
   sudo docker-compose ps
   ```

4. View server logs:
   ```
   sudo docker-compose logs -f server
   ```

5. Test the API:
   ```
   curl http://localhost:8081/api/health
   ```

## Stopping the Services

To stop the containers:
```
sudo docker-compose down
```

To stop and delete the volumes (this will delete all data):
```
sudo docker-compose down -v
```

## Default Users

The seed script creates two default users:

1. Admin User:
   - Username: admin
   - Password: password

2. Regular User:
   - Username: user
   - Password: password

## Customizing Configuration

You can modify the environment variables in the `docker-compose.yml` file to customize settings like:
- Database credentials
- JWT secrets
- Encryption keys

For production use, make sure to change all default secrets and passwords!

## Troubleshooting

If the server fails to connect to the database:
1. Check if the PostgreSQL container is running
2. Restart the server container:
   ```
   sudo docker-compose restart server
   ```

If you need to rebuild the server container after code changes:
```
sudo docker-compose build server
sudo docker-compose up -d server
``` 