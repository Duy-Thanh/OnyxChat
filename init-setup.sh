#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== OnyxChat Development Setup ==="
echo "This script will set up the OnyxChat development environment"
echo "and start the server using Docker."

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "Error: Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "Error: Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Create necessary directories
mkdir -p nodejs-server/src/db

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    echo "Warning: Docker daemon is not running."
    echo "Would you like to start Docker? (y/n)"
    read -r response
    if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
        echo "Starting Docker service..."
        sudo systemctl start docker
        sleep 5
        if ! docker info &> /dev/null; then
            echo "Error: Failed to start Docker daemon."
            exit 1
        fi
        echo "Docker daemon started successfully."
    else
        echo "Please start Docker manually and run this script again."
        exit 1
    fi
fi

echo "=== Stopping any existing OnyxChat containers ==="
sudo docker-compose down || true

echo "=== Building and starting OnyxChat containers ==="
sudo docker-compose up -d --build

echo "=== Checking container status ==="
sleep 5
sudo docker-compose ps

echo ""
echo "=== Setup completed successfully! ==="
echo "The OnyxChat server is now running at:"
echo "- API: http://localhost:8082"
echo "- WebSocket: ws://localhost:8082/ws"
echo ""
echo "To stop the server, run: sudo docker-compose down"
echo "To view logs, run: sudo docker-compose logs -f"
echo ""
echo "For Android development, use '10.0.2.2:8082' as the server address in the emulator." 