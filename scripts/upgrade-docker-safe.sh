#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}===== Starting OnyxChat Docker Upgrade Process =====\n${NC}"

# Get the absolute path of the project root directory
PROJECT_ROOT="/home/nekkochan/AndroidStudioProjects/onyxchat"
cd "$PROJECT_ROOT"

# Create timestamp for this upgrade
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$PROJECT_ROOT/backups/upgrade_${TIMESTAMP}"

# Create backup directory immediately
echo -e "${GREEN}Creating backup directory at ${BACKUP_DIR}${NC}"
mkdir -p "${BACKUP_DIR}"

# Function to handle errors
error_handler() {
  echo -e "${RED}Error occurred on line $1. Exiting...${NC}"
  
  # Ask if user wants to attempt restoration
  read -p "Would you like to attempt to restore from backup? (y/n) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Attempting to restore from backup at ${BACKUP_DIR}...${NC}"
    if [ -d "$BACKUP_DIR" ] && [ -f "$BACKUP_DIR/restore.sh" ]; then
      cd "$BACKUP_DIR"
      bash ./restore.sh
      cd "$PROJECT_ROOT"
      echo -e "${GREEN}Restoration attempted. Please check your system to verify.${NC}"
    else
      echo -e "${RED}No valid backup found at ${BACKUP_DIR}${NC}"
    fi
  fi
  
  exit 1
}

# Set up error handling
trap 'error_handler $LINENO' ERR

# First remove version attribute from docker-compose files if they exist
echo -e "${YELLOW}Checking for obsolete 'version' attribute in docker-compose files...${NC}"
if [ -f "$PROJECT_ROOT/docker-compose.prod.yml" ] && grep -q "^version:" "$PROJECT_ROOT/docker-compose.prod.yml"; then
  sed -i '/^version:/d' "$PROJECT_ROOT/docker-compose.prod.yml"
  echo -e "${GREEN}Removed 'version' attribute from docker-compose.prod.yml${NC}"
elif [ -f "$PROJECT_ROOT/docker-compose.prod.yml" ]; then
  echo -e "${GREEN}No obsolete 'version' attribute in docker-compose.prod.yml${NC}"
else
  echo -e "${YELLOW}File docker-compose.prod.yml not found${NC}"
fi

if [ -f "$PROJECT_ROOT/docker-compose.yml" ] && grep -q "^version:" "$PROJECT_ROOT/docker-compose.yml"; then
  sed -i '/^version:/d' "$PROJECT_ROOT/docker-compose.yml"
  echo -e "${GREEN}Removed 'version' attribute from docker-compose.yml${NC}"
elif [ -f "$PROJECT_ROOT/docker-compose.yml" ]; then
  echo -e "${GREEN}No obsolete 'version' attribute in docker-compose.yml${NC}"
else
  echo -e "${YELLOW}File docker-compose.yml not found${NC}"
fi

# Function to wait for container to be ready
wait_for_container() {
  local container=$1
  local max_attempts=30
  local attempt=1
  
  echo -e "${YELLOW}Waiting for $container to be ready...${NC}"
  
  while [ $attempt -le $max_attempts ]; do
    if [ -f "$PROJECT_ROOT/docker-compose.prod.yml" ] && sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" ps $container | grep -q "Up"; then
      # Add extra delay to ensure container is fully initialized
      sleep 5
      echo -e "${GREEN}$container is ready${NC}"
      return 0
    fi
    
    echo -e "${YELLOW}Attempt $attempt/$max_attempts: $container is not ready yet...${NC}"
    sleep 5
    ((attempt++))
  done
  
  echo -e "${RED}Timed out waiting for $container to be ready${NC}"
  return 1
}

# Create a custom backup file for key data
echo -e "${YELLOW}Creating a direct backup of critical data...${NC}"

# Backup docker-compose files
echo -e "${GREEN}Backing up Docker configuration files...${NC}"
[ -f "$PROJECT_ROOT/docker-compose.yml" ] && cp "$PROJECT_ROOT/docker-compose.yml" "${BACKUP_DIR}/docker-compose.yml.bak" || echo -e "${YELLOW}No docker-compose.yml file found${NC}"
[ -f "$PROJECT_ROOT/docker-compose.prod.yml" ] && cp "$PROJECT_ROOT/docker-compose.prod.yml" "${BACKUP_DIR}/docker-compose.prod.yml.bak" || echo -e "${YELLOW}No docker-compose.prod.yml file found${NC}"
[ -f "$PROJECT_ROOT/nginx.Dockerfile" ] && cp "$PROJECT_ROOT/nginx.Dockerfile" "${BACKUP_DIR}/nginx.Dockerfile.bak" || echo -e "${YELLOW}No nginx.Dockerfile file found${NC}"
[ -d "$PROJECT_ROOT/nodejs-server" ] && find "$PROJECT_ROOT/nodejs-server" -name "Dockerfile*" -exec cp {} "${BACKUP_DIR}/" \; || echo -e "${YELLOW}No Dockerfile files found in nodejs-server${NC}"
[ -f "$PROJECT_ROOT/nginx.conf" ] && cp "$PROJECT_ROOT/nginx.conf" "${BACKUP_DIR}/nginx.conf.bak" || echo -e "${YELLOW}No nginx.conf file found${NC}"
[ -f "$PROJECT_ROOT/.env.production" ] && cp "$PROJECT_ROOT/.env.production" "${BACKUP_DIR}/.env.production.bak" || echo -e "${YELLOW}No .env.production file found${NC}"

# Check if docker-compose.prod.yml exists before proceeding
if [ ! -f "$PROJECT_ROOT/docker-compose.prod.yml" ]; then
  echo -e "${RED}ERROR: docker-compose.prod.yml not found. Cannot continue with upgrade.${NC}"
  exit 1
fi

# Backup PostgreSQL data using direct-postgres-backup.sh if available
if [ -f "$PROJECT_ROOT/scripts/direct-postgres-backup.sh" ]; then
  echo -e "${GREEN}Backing up PostgreSQL database using direct-postgres-backup.sh...${NC}"
  bash "$PROJECT_ROOT/scripts/direct-postgres-backup.sh"
else
  echo -e "${YELLOW}direct-postgres-backup.sh not found, skipping database backup${NC}"
fi

# Step 2: Update Docker image references in configuration files
echo -e "\n${YELLOW}STEP 2: Updating Docker image versions in configuration files...${NC}"
if [ -f "$PROJECT_ROOT/scripts/update-docker-images.sh" ]; then
  bash "$PROJECT_ROOT/scripts/update-docker-images.sh"
else
  echo -e "${YELLOW}update-docker-images.sh not found, skipping image update${NC}"
fi

# Step 3: Stop running containers
echo -e "\n${YELLOW}STEP 3: Stopping currently running containers...${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" down
echo -e "${GREEN}All containers stopped successfully${NC}"

# Step 4: Pull new Docker images
echo -e "\n${YELLOW}STEP 4: Pulling latest Docker images...${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" pull
echo -e "${GREEN}All images pulled successfully${NC}"

# Step 5: Rebuild custom images
echo -e "\n${YELLOW}STEP 5: Rebuilding custom Docker images...${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" build
echo -e "${GREEN}Custom images built successfully${NC}"

# Step 6: Start updated containers
echo -e "\n${YELLOW}STEP 6: Starting updated containers...${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" up -d
echo -e "${GREEN}Containers started in detached mode${NC}"

# Wait for containers to be ready
echo -e "\n${YELLOW}Waiting for containers to initialize...${NC}"
sleep 20  # Initial wait for containers to start

# Check if PostgreSQL is ready
wait_for_container "postgres" || {
  echo -e "${RED}PostgreSQL container failed to start properly.${NC}"
  echo -e "${YELLOW}Continuing with the upgrade process...${NC}"
}

# Step 7: Wait for services to be healthy
echo -e "\n${YELLOW}STEP 7: Waiting for services to become healthy...${NC}"
attempt=1
max_attempts=20
all_healthy=0

# Initialize health variables
pg_healthy=0
redis_healthy=0
server_healthy=0
nginx_healthy=0

while [ $attempt -le $max_attempts ] && [ $all_healthy -eq 0 ]; do
  echo -e "Attempt $attempt of $max_attempts - Checking service health..."
  
  # Check PostgreSQL
  if sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" ps | grep -q "postgres.*Up" && \
     sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" exec -T postgres pg_isready -U ${DB_USER:-postgres} > /dev/null 2>&1; then
    echo -e "${GREEN}PostgreSQL is healthy${NC}"
    pg_healthy=1
  else
    echo -e "${YELLOW}PostgreSQL is not yet ready...${NC}"
    pg_healthy=0
  fi
  
  # Check Redis
  if sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" ps | grep -q "redis.*Up" && \
     sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" exec -T redis redis-cli PING 2>/dev/null | grep -q "PONG"; then
    echo -e "${GREEN}Redis is healthy${NC}"
    redis_healthy=1
  else
    echo -e "${YELLOW}Redis is not yet ready...${NC}"
    redis_healthy=0
  fi
  
  # Check Node.js Server - don't fail if curl fails
  if curl -s http://localhost:${HTTP_PORT:-80}/api/status 2>/dev/null | grep -q "\"status\":\"success\""; then
    echo -e "${GREEN}Node.js server is healthy${NC}"
    server_healthy=1
  else
    echo -e "${YELLOW}Node.js server is not yet ready...${NC}"
    server_healthy=0
  fi
  
  # Check Nginx - don't fail if curl fails
  if curl -s -I http://localhost:${HTTP_PORT:-80} 2>/dev/null | grep -q "200 OK\|301 Moved Permanently\|302 Found"; then
    echo -e "${GREEN}Nginx is healthy${NC}"
    nginx_healthy=1
  else
    echo -e "${YELLOW}Nginx is not yet ready...${NC}"
    nginx_healthy=0
  fi
  
  if [ $pg_healthy -eq 1 ] && [ $redis_healthy -eq 1 ] && [ $server_healthy -eq 1 ] && [ $nginx_healthy -eq 1 ]; then
    all_healthy=1
  else
    echo -e "${YELLOW}Not all services are healthy yet. Waiting 10 seconds before next check...${NC}"
    sleep 10
    ((attempt++))
  fi
done

if [ $all_healthy -eq 1 ]; then
  echo -e "${GREEN}All services are healthy!${NC}"
else
  echo -e "${YELLOW}Warning: Not all services are reporting as healthy. You may need to investigate.${NC}"
  echo -e "Check logs with: sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" logs"
fi

# Step 8: Display container status
echo -e "\n${YELLOW}STEP 8: Current container status:${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" ps

# Step 9: Show a brief log summary
echo -e "\n${YELLOW}STEP 9: Brief log summary (last 5 lines per service):${NC}"
echo -e "${GREEN}Server logs:${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" logs --tail=5 server
echo -e "\n${GREEN}Nginx logs:${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" logs --tail=5 nginx
echo -e "\n${GREEN}PostgreSQL logs:${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" logs --tail=5 postgres
echo -e "\n${GREEN}Redis logs:${NC}"
sudo docker-compose -f "$PROJECT_ROOT/docker-compose.prod.yml" logs --tail=5 redis

echo -e "\n${GREEN}===== Docker upgrade process completed successfully! =====\n${NC}"
echo -e "${YELLOW}If you encounter any issues, you can:${NC}"
echo -e "1. Check detailed logs with: ${GREEN}sudo docker-compose -f \"$PROJECT_ROOT/docker-compose.prod.yml\" logs${NC}"
echo -e "2. Restore from backup with: ${GREEN}cd ${BACKUP_DIR} && ./restore.sh${NC}"
echo -e "\nBackup location: ${GREEN}${BACKUP_DIR}${NC}\n" 