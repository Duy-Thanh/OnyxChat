#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}===== Starting OnyxChat Docker Upgrade Process =====\n${NC}"

# Capture current directory for reference
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

# Create timestamp for this upgrade
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="./backups/upgrade_${TIMESTAMP}"

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
      cd "$SCRIPT_DIR"
      echo -e "${GREEN}Restoration attempted. Please check your system to verify.${NC}"
    else
      echo -e "${RED}No valid backup found at ${BACKUP_DIR}${NC}"
    fi
  fi
  
  exit 1
}

# Set up error handling
trap 'error_handler $LINENO' ERR

# Step 0: Check PostgreSQL compatibility
echo -e "${YELLOW}STEP 0: Checking PostgreSQL compatibility for upgrade from 14 to 16...${NC}"
echo -e "Analyzing PostgreSQL database for potential compatibility issues..."

# Check if PostgreSQL is running
if sudo docker-compose -f docker-compose.prod.yml ps | grep -q "postgres.*Up"; then
  # Save current postgresql.conf settings
  echo -e "Creating a PostgreSQL configuration backup..."
  sudo docker-compose -f docker-compose.prod.yml exec -T postgres sh -c "cat /var/lib/postgresql/data/postgresql.conf" > "${BACKUP_DIR}/postgresql.conf.bak" || echo -e "${YELLOW}Could not save PostgreSQL configuration, continuing anyway...${NC}"
  
  # Check for any PostgreSQL incompatibilities
  incompatibilities=$(sudo docker-compose -f docker-compose.prod.yml exec -T postgres psql -U ${DB_USER:-postgres} -d ${DB_NAME:-onyxchat} -c "
    SELECT c.relname, a.attname
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid
    JOIN pg_catalog.pg_type t ON a.atttypid = t.oid
    WHERE c.relkind = 'r' 
    AND NOT a.attisdropped
    AND t.typname IN ('money', 'abstime', 'reltime', 'tinterval');
  " || echo "Failed to check incompatibilities")
  
  if echo "$incompatibilities" | grep -q "row\|rows"; then
    echo -e "${YELLOW}Warning: Found data types that may be problematic in PostgreSQL 16:${NC}"
    echo "$incompatibilities"
    echo -e "${YELLOW}Consider creating a more comprehensive backup and testing the upgrade in a non-production environment first.${NC}"
    
    read -p "Do you want to continue with the upgrade anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
      echo -e "${RED}Upgrade aborted by user.${NC}"
      exit 1
    fi
  else
    echo -e "${GREEN}No known PostgreSQL compatibility issues detected.${NC}"
  fi
else
  echo -e "${YELLOW}PostgreSQL container not running, skipping compatibility check.${NC}"
  echo -e "${YELLOW}WARNING: It's recommended to run this script when PostgreSQL is running to check for compatibility issues.${NC}"
  
  read -p "Do you want to continue with the upgrade anyway? (y/n) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}Upgrade aborted by user.${NC}"
    exit 1
  fi
fi

# Step 1: Create backup
echo -e "${YELLOW}STEP 1: Creating backup before upgrade...${NC}"
if bash ./backup-before-upgrade.sh; then
  echo -e "${GREEN}Backup completed successfully at ${BACKUP_DIR}${NC}"
else
  echo -e "${YELLOW}WARNING: Backup script completed with errors. Check the messages above.${NC}"
  read -p "Would you like to continue with the upgrade anyway? (y/n) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}Upgrade aborted by user.${NC}"
    exit 1
  fi
  echo -e "${YELLOW}Continuing with upgrade despite backup issues...${NC}"
fi

# Step 2: Update Docker image references in configuration files
echo -e "\n${YELLOW}STEP 2: Updating Docker image versions in configuration files...${NC}"
bash ./update-docker-images.sh

# Step 3: Stop running containers
echo -e "\n${YELLOW}STEP 3: Stopping currently running containers...${NC}"
sudo docker-compose -f docker-compose.prod.yml down
echo -e "${GREEN}All containers stopped successfully${NC}"

# Step 4: Pull new Docker images
echo -e "\n${YELLOW}STEP 4: Pulling latest Docker images...${NC}"
sudo docker-compose -f docker-compose.prod.yml pull
echo -e "${GREEN}All images pulled successfully${NC}"

# Step 5: Rebuild custom images
echo -e "\n${YELLOW}STEP 5: Rebuilding custom Docker images...${NC}"
sudo docker-compose -f docker-compose.prod.yml build
echo -e "${GREEN}Custom images built successfully${NC}"

# Step 6: Start updated containers
echo -e "\n${YELLOW}STEP 6: Starting updated containers...${NC}"
sudo docker-compose -f docker-compose.prod.yml up -d
echo -e "${GREEN}Containers started in detached mode${NC}"

# Step 7: Wait for services to be healthy
echo -e "\n${YELLOW}STEP 7: Waiting for services to become healthy...${NC}"
attempt=1
max_attempts=10
all_healthy=0

# Initialize health variables
pg_healthy=0
redis_healthy=0
server_healthy=0
nginx_healthy=0

while [ $attempt -le $max_attempts ] && [ $all_healthy -eq 0 ]; do
  echo -e "Attempt $attempt of $max_attempts - Checking service health..."
  
  # Check PostgreSQL
  if sudo docker-compose -f docker-compose.prod.yml exec -T postgres pg_isready -U ${DB_USER:-postgres} > /dev/null 2>&1; then
    echo -e "${GREEN}PostgreSQL is healthy${NC}"
    pg_healthy=1
  else
    echo -e "${YELLOW}PostgreSQL is not yet ready...${NC}"
    pg_healthy=0
  fi
  
  # Check Redis
  if sudo docker-compose -f docker-compose.prod.yml exec -T redis redis-cli -a ${REDIS_PASSWORD:-redis} PING 2>/dev/null | grep -q "PONG"; then
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
  echo -e "Check logs with: sudo docker-compose -f docker-compose.prod.yml logs"
fi

# Step 8: Display container status
echo -e "\n${YELLOW}STEP 8: Current container status:${NC}"
sudo docker-compose -f docker-compose.prod.yml ps

# Step 9: Show a brief log summary
echo -e "\n${YELLOW}STEP 9: Brief log summary (last 5 lines per service):${NC}"
echo -e "${GREEN}Server logs:${NC}"
sudo docker-compose -f docker-compose.prod.yml logs --tail=5 server
echo -e "\n${GREEN}Nginx logs:${NC}"
sudo docker-compose -f docker-compose.prod.yml logs --tail=5 nginx
echo -e "\n${GREEN}PostgreSQL logs:${NC}"
sudo docker-compose -f docker-compose.prod.yml logs --tail=5 postgres
echo -e "\n${GREEN}Redis logs:${NC}"
sudo docker-compose -f docker-compose.prod.yml logs --tail=5 redis

echo -e "\n${GREEN}===== Docker upgrade process completed successfully! =====\n${NC}"
echo -e "${YELLOW}If you encounter any issues, you can:${NC}"
echo -e "1. Check detailed logs with: ${GREEN}sudo docker-compose -f docker-compose.prod.yml logs${NC}"
echo -e "2. Restore from backup with: ${GREEN}cd ${BACKUP_DIR} && ./restore.sh${NC}"
echo -e "\nBackup location: ${GREEN}${BACKUP_DIR}${NC}\n" 