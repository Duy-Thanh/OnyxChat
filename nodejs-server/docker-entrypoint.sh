#!/bin/sh
set -e

# Function for logging
log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

# Function to handle errors
handle_error() {
    log "ERROR: $1"
    exit 1
}

log "Starting OnyxChat production server..."

# Environment validation with better error messages
if [ -z "$JWT_SECRET" ]; then
    handle_error "JWT_SECRET environment variable is not set. Please define it in your .env file or Docker environment."
fi

if [ -z "$JWT_REFRESH_SECRET" ]; then
    handle_error "JWT_REFRESH_SECRET environment variable is not set. Please define it in your .env file or Docker environment."
fi

if [ -z "$ENCRYPTION_KEY" ]; then
    handle_error "ENCRYPTION_KEY environment variable is not set. Please define it in your .env file or Docker environment."
fi

# Debug output for troubleshooting (removed in final version)
log "Environment variables:"
log "JWT_SECRET length: ${#JWT_SECRET} characters"
log "JWT_REFRESH_SECRET length: ${#JWT_REFRESH_SECRET} characters"
log "ENCRYPTION_KEY length: ${#ENCRYPTION_KEY} characters"

log "Waiting for PostgreSQL to start..."
# Use a simple approach to wait for PostgreSQL
# Retry multiple times with longer wait between attempts
RETRIES=30
RETRY_COUNT=0
until [ $RETRY_COUNT -eq $RETRIES ] || nc -z -v -w30 postgres 5432
do
    RETRY_COUNT=$((RETRY_COUNT+1))
    log "Waiting for PostgreSQL to start... (attempt $RETRY_COUNT/$RETRIES)"
    sleep 5
done

if [ $RETRY_COUNT -eq $RETRIES ]; then
    handle_error "Failed to connect to PostgreSQL after multiple attempts"
fi

log "PostgreSQL is up and running!"

# Check if we need to run migrations
if [ "${SKIP_MIGRATIONS:-false}" != "true" ]; then
    log "Running database migrations..."
    node src/db/migrate.js || handle_error "Database migration failed"

    # Only seed in specific environments if requested
    if [ "${RUN_SEED:-false}" = "true" ]; then
        log "Running database seed..."
        node src/db/seed.js || handle_error "Database seed failed"
    fi
else
    log "Skipping database migrations as requested"
fi

# Set up HTTPS if enabled
if [ "${ENABLE_HTTPS:-false}" = "true" ]; then
    if [ ! -f "$SSL_CERT_PATH" ] || [ ! -f "$SSL_KEY_PATH" ]; then
        handle_error "HTTPS is enabled but SSL certificate or key is missing"
    fi
    log "HTTPS is enabled. Using SSL certificates."
fi

# Start the server
log "Starting OnyxChat server..."
exec node src/server.js 