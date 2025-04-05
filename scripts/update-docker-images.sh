#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Get the absolute path of the project root directory
PROJECT_ROOT="/home/nekkochan/AndroidStudioProjects/onyxchat"
cd "$PROJECT_ROOT"

echo -e "${YELLOW}Starting Docker image version update...${NC}"

# Check if required files exist
if [ ! -f "$PROJECT_ROOT/docker-compose.prod.yml" ]; then
  echo -e "${RED}Error: docker-compose.prod.yml not found.${NC}"
  exit 1
fi

# Update postgres image in docker-compose.prod.yml
if [ -f "$PROJECT_ROOT/docker-compose.prod.yml" ]; then
  echo -e "${GREEN}Updating PostgreSQL from postgres:14-alpine to postgres:16-alpine in docker-compose.prod.yml${NC}"
  sed -i 's/postgres:14-alpine/postgres:16-alpine/g' "$PROJECT_ROOT/docker-compose.prod.yml"
else
  echo -e "${YELLOW}docker-compose.prod.yml not found, skipping PostgreSQL update${NC}"
fi

# Update PostgreSQL in docker-compose.yml (not Alpine version)
if [ -f "$PROJECT_ROOT/docker-compose.yml" ]; then
  echo -e "${GREEN}Updating PostgreSQL from postgres:14 to postgres:16 in docker-compose.yml${NC}"
  sed -i 's/postgres:14/postgres:16/g' "$PROJECT_ROOT/docker-compose.yml"
else
  echo -e "${YELLOW}docker-compose.yml not found, skipping PostgreSQL update${NC}"
fi

# Update Redis in docker-compose.prod.yml
if [ -f "$PROJECT_ROOT/docker-compose.prod.yml" ]; then
  echo -e "${GREEN}Updating Redis from redis:7-alpine to redis:7.2-alpine in docker-compose.prod.yml${NC}"
  sed -i 's/redis:7-alpine/redis:7.2-alpine/g' "$PROJECT_ROOT/docker-compose.prod.yml"
else
  echo -e "${YELLOW}docker-compose.prod.yml not found, skipping Redis update${NC}"
fi

# Update backup service image
if [ -f "$PROJECT_ROOT/docker-compose.prod.yml" ]; then
  echo -e "${GREEN}Updating backup service from postgres:14-alpine to postgres:16-alpine in docker-compose.prod.yml${NC}"
  sed -i 's/image: postgres:14-alpine/image: postgres:16-alpine/g' "$PROJECT_ROOT/docker-compose.prod.yml"
else
  echo -e "${YELLOW}docker-compose.prod.yml not found, skipping backup service update${NC}"
fi

# Update nginx image in nginx.Dockerfile
if [ -f "$PROJECT_ROOT/nginx.Dockerfile" ]; then
  echo -e "${GREEN}Ensuring nginx image is set to nginx:mainline-alpine in nginx.Dockerfile${NC}"
  sed -i 's/FROM nginx:.*/FROM nginx:mainline-alpine/g' "$PROJECT_ROOT/nginx.Dockerfile"
else
  echo -e "${YELLOW}nginx.Dockerfile not found, skipping nginx image update${NC}"
fi

# Update node image in nodejs-server/Dockerfile and Dockerfile.production
if [ -d "$PROJECT_ROOT/nodejs-server" ]; then
  if [ -f "$PROJECT_ROOT/nodejs-server/Dockerfile" ]; then
    echo -e "${GREEN}Ensuring nodejs image is set to node:20-alpine in Dockerfile${NC}"
    sed -i 's/FROM node:.*/FROM node:20-alpine/g' "$PROJECT_ROOT/nodejs-server/Dockerfile"
  else
    echo -e "${YELLOW}nodejs-server/Dockerfile not found, skipping node image update${NC}"
  fi
  
  if [ -f "$PROJECT_ROOT/nodejs-server/Dockerfile.production" ]; then
    echo -e "${GREEN}Ensuring nodejs image is set to node:20-alpine in Dockerfile.production${NC}"
    sed -i 's/FROM node:.*AS builder/FROM node:20-alpine AS builder/g' "$PROJECT_ROOT/nodejs-server/Dockerfile.production"
    sed -i 's/FROM node:.*$/FROM node:20-alpine/g' "$PROJECT_ROOT/nodejs-server/Dockerfile.production"
  else
    echo -e "${YELLOW}nodejs-server/Dockerfile.production not found, skipping node image update${NC}"
  fi
else
  echo -e "${YELLOW}nodejs-server directory not found, skipping node image updates${NC}"
fi

echo -e "${GREEN}Docker image versions updated successfully!${NC}"
echo -e "${YELLOW}Next steps:${NC}"
echo -e "1. ${YELLOW}Run ./backup-before-upgrade.sh to create a backup${NC}"
echo -e "2. ${YELLOW}Stop running containers: docker-compose -f docker-compose.prod.yml down${NC}"
echo -e "3. ${YELLOW}Pull new images: docker-compose -f docker-compose.prod.yml pull${NC}"
echo -e "4. ${YELLOW}Rebuild custom images: docker-compose -f docker-compose.prod.yml build${NC}"
echo -e "5. ${YELLOW}Start updated containers: docker-compose -f docker-compose.prod.yml up -d${NC}"
echo -e "6. ${YELLOW}Check for any issues in logs: docker-compose -f docker-compose.prod.yml logs${NC}" 