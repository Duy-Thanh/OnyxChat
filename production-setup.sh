#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_banner() {
    echo -e "${BLUE}"
    echo "=============================================="
    echo "      OnyxChat Production Deployment"
    echo "=============================================="
    echo -e "${NC}"
}

log() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] $1${NC}"
}

warning() {
    echo -e "${YELLOW}[WARNING] $1${NC}"
}

error() {
    echo -e "${RED}[ERROR] $1${NC}"
    exit 1
}

print_banner

log "Starting OnyxChat production setup..."

# Check if running as root
if [ "$(id -u)" != "0" ]; then
    error "This script must be run as root or with sudo"
fi

# Check requirements
log "Checking system requirements..."

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    error "Docker is not installed. Please install Docker first."
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    error "Docker Compose is not installed. Please install Docker Compose first."
fi

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    error "Docker daemon is not running. Please start Docker service."
fi

# Check for .env.production file
if [ ! -f ".env.production" ]; then
    warning "No .env.production file found. Creating from example..."
    if [ -f ".env.production.example" ]; then
        cp .env.production.example .env.production
        warning "Please edit .env.production to set secure values before proceeding."
        warning "Press Enter to continue after editing the file, or Ctrl+C to abort."
        read -r
    else
        error ".env.production.example not found. Cannot create environment file."
    fi
fi

# Create necessary directories
log "Creating required directories..."
mkdir -p certs
mkdir -p logs
mkdir -p nodejs-server/src/db

# Check for SSL certificates if HTTPS is enabled
ENABLE_HTTPS=$(grep '^ENABLE_HTTPS=' .env.production | cut -d '=' -f2)
if [ "$ENABLE_HTTPS" = "true" ]; then
    SSL_CERT_PATH=$(grep '^SSL_CERT_PATH=' .env.production | cut -d '=' -f2 | sed 's/^\/certs\///')
    SSL_KEY_PATH=$(grep '^SSL_KEY_PATH=' .env.production | cut -d '=' -f2 | sed 's/^\/certs\///')
    
    if [ ! -f "certs/$SSL_CERT_PATH" ] || [ ! -f "certs/$SSL_KEY_PATH" ]; then
        warning "HTTPS is enabled but certificates not found. You'll need to provide them."
        warning "Paths should be: certs/$SSL_CERT_PATH and certs/$SSL_KEY_PATH"
    fi
fi

# Generate secure values for secrets if they're empty
log "Checking security credentials..."
JWT_SECRET=$(grep '^JWT_SECRET=' .env.production | cut -d '=' -f2)
JWT_REFRESH_SECRET=$(grep '^JWT_REFRESH_SECRET=' .env.production | cut -d '=' -f2)
ENCRYPTION_KEY=$(grep '^ENCRYPTION_KEY=' .env.production | cut -d '=' -f2)

if [ -z "$JWT_SECRET" ] || [ "$JWT_SECRET" = " " ]; then
    warning "JWT_SECRET is not set. Generating a secure value..."
    NEW_SECRET=$(openssl rand -base64 32)
    sed -i "s/^JWT_SECRET=.*/JWT_SECRET=$NEW_SECRET/" .env.production
fi

if [ -z "$JWT_REFRESH_SECRET" ] || [ "$JWT_REFRESH_SECRET" = " " ]; then
    warning "JWT_REFRESH_SECRET is not set. Generating a secure value..."
    NEW_SECRET=$(openssl rand -base64 32)
    sed -i "s/^JWT_REFRESH_SECRET=.*/JWT_REFRESH_SECRET=$NEW_SECRET/" .env.production
fi

if [ -z "$ENCRYPTION_KEY" ] || [ "$ENCRYPTION_KEY" = " " ]; then
    warning "ENCRYPTION_KEY is not set. Generating a secure value..."
    NEW_KEY=$(openssl rand -base64 24)
    sed -i "s/^ENCRYPTION_KEY=.*/ENCRYPTION_KEY=$NEW_KEY/" .env.production
fi

# Ensure postgres password is changed from default
DB_PASSWORD=$(grep '^DB_PASSWORD=' .env.production | cut -d '=' -f2)
if [ "$DB_PASSWORD" = "postgres" ]; then
    warning "Database password is set to default value. This is insecure for production."
    warning "Would you like to generate a secure password? (y/n)"
    read -r response
    if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
        NEW_PASSWORD=$(openssl rand -base64 12)
        sed -i "s/^DB_PASSWORD=.*/DB_PASSWORD=$NEW_PASSWORD/" .env.production
        log "Password updated. Make sure to remember this password: $NEW_PASSWORD"
    else
        warning "Continuing with default password. NOT RECOMMENDED FOR PRODUCTION!"
    fi
fi

# Stop any existing containers
log "Stopping any existing OnyxChat containers..."
docker-compose -f docker-compose.yml down || true

# Build and start production containers
log "Building and starting OnyxChat production containers..."
docker-compose -f docker-compose.yml up -d --build

# Check container status
log "Checking container status..."
sleep 5
docker-compose -f docker-compose.yml ps

# Output success message
log "Setup completed successfully!"
echo ""
echo -e "${GREEN}The OnyxChat server is now running at:${NC}"
SERVER_PORT=$(grep '^SERVER_PORT=' .env.production | cut -d '=' -f2 || echo "8082")
if [ "$ENABLE_HTTPS" = "true" ]; then
    echo -e "${GREEN}- API: https://your-domain:$SERVER_PORT${NC}"
    echo -e "${GREEN}- WebSocket: wss://your-domain:$SERVER_PORT/ws${NC}"
else
    echo -e "${GREEN}- API: http://your-domain:$SERVER_PORT${NC}"
    echo -e "${GREEN}- WebSocket: ws://your-domain:$SERVER_PORT/ws${NC}"
fi
echo ""
echo -e "${BLUE}For Android development, use '10.0.2.2:$SERVER_PORT' as the server address in the emulator.${NC}" 
echo ""
echo -e "${YELLOW}To stop the server:${NC} docker-compose -f docker-compose.yml down"
echo -e "${YELLOW}To view logs:${NC} docker-compose -f docker-compose.yml logs -f"
echo -e "${YELLOW}To restart the server:${NC} docker-compose -f docker-compose.yml restart" 