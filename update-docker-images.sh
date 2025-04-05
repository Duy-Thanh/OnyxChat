#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting Docker image version update...${NC}"

# Remove version attribute from docker-compose files
echo -e "${GREEN}Removing obsolete 'version' attribute from docker-compose files...${NC}"
sed -i '/^version:/d' docker-compose.prod.yml
sed -i '/^version:/d' docker-compose.yml

# Update postgres image in docker-compose.prod.yml
echo -e "${GREEN}Updating PostgreSQL from postgres:14-alpine to postgres:16-alpine in docker-compose.prod.yml${NC}"
sed -i 's/postgres:14-alpine/postgres:16-alpine/g' docker-compose.prod.yml

# Update PostgreSQL in docker-compose.yml (not Alpine version)
echo -e "${GREEN}Updating PostgreSQL from postgres:14 to postgres:16 in docker-compose.yml${NC}"
sed -i 's/postgres:14/postgres:16/g' docker-compose.yml

# Update Redis in docker-compose.prod.yml
echo -e "${GREEN}Updating Redis from redis:7-alpine to redis:7.2-alpine in docker-compose.prod.yml${NC}"
sed -i 's/redis:7-alpine/redis:7.2-alpine/g' docker-compose.prod.yml

# Update backup service image
echo -e "${GREEN}Updating backup service from postgres:14-alpine to postgres:16-alpine in docker-compose.prod.yml${NC}"
sed -i 's/image: postgres:14-alpine/image: postgres:16-alpine/g' docker-compose.prod.yml

# Update nginx image in nginx.Dockerfile
echo -e "${GREEN}Ensuring nginx image is set to nginx:mainline-alpine in nginx.Dockerfile${NC}"
sed -i 's/FROM nginx:.*/FROM nginx:mainline-alpine/g' nginx.Dockerfile

# Update node image in nodejs-server/Dockerfile and Dockerfile.production
echo -e "${GREEN}Ensuring nodejs image is set to node:20-alpine in Dockerfile and Dockerfile.production${NC}"
sed -i 's/FROM node:20-alpine/FROM node:20-alpine/g' nodejs-server/Dockerfile
sed -i 's/FROM node:20-alpine AS builder/FROM node:20-alpine AS builder/g' nodejs-server/Dockerfile.production
sed -i 's/FROM node:20-alpine$/FROM node:20-alpine/g' nodejs-server/Dockerfile.production

echo -e "${GREEN}Docker image versions updated successfully!${NC}"
echo -e "${YELLOW}Next steps:${NC}"
echo -e "1. ${YELLOW}Run ./backup-before-upgrade.sh to create a backup${NC}"
echo -e "2. ${YELLOW}Stop running containers: docker-compose -f docker-compose.prod.yml down${NC}"
echo -e "3. ${YELLOW}Pull new images: docker-compose -f docker-compose.prod.yml pull${NC}"
echo -e "4. ${YELLOW}Rebuild custom images: docker-compose -f docker-compose.prod.yml build${NC}"
echo -e "5. ${YELLOW}Start updated containers: docker-compose -f docker-compose.prod.yml up -d${NC}"
echo -e "6. ${YELLOW}Check for any issues in logs: docker-compose -f docker-compose.prod.yml logs${NC}" 