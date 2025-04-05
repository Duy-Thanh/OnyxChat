#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting backup before Docker upgrade...${NC}"

# Create timestamp for backup directory
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="./backups/upgrade_${TIMESTAMP}"

# Create backup directory
echo -e "${GREEN}Creating backup directory at ${BACKUP_DIR}${NC}"
mkdir -p "${BACKUP_DIR}"

# Backup docker-compose files
echo -e "${GREEN}Backing up Docker configuration files...${NC}"
cp docker-compose.yml "${BACKUP_DIR}/docker-compose.yml.bak"
cp docker-compose.prod.yml "${BACKUP_DIR}/docker-compose.prod.yml.bak"
cp nginx.Dockerfile "${BACKUP_DIR}/nginx.Dockerfile.bak"
cp -r nodejs-server/Dockerfile* "${BACKUP_DIR}/"
cp nginx.conf "${BACKUP_DIR}/nginx.conf.bak"
cp .env.production "${BACKUP_DIR}/.env.production.bak" || echo -e "${YELLOW}No .env.production file found${NC}"

# Backup database
echo -e "${GREEN}Creating PostgreSQL database backup...${NC}"
if docker-compose -f docker-compose.prod.yml exec -T postgres pg_dump -U ${DB_USER:-postgres} ${DB_NAME:-onyxchat} > "${BACKUP_DIR}/database_backup.sql"; then
    echo -e "${GREEN}Database backup completed successfully${NC}"
else
    echo -e "${YELLOW}Trying alternative backup method...${NC}"
    # Try using the backup container directly
    docker-compose -f docker-compose.prod.yml exec -T backup pg_dump -h postgres -U ${DB_USER:-postgres} ${DB_NAME:-onyxchat} > "${BACKUP_DIR}/database_backup.sql"
    echo -e "${GREEN}Database backup completed using backup container${NC}"
fi

# Backup Redis data
echo -e "${GREEN}Creating Redis backup...${NC}"
mkdir -p "${BACKUP_DIR}/redis"
if docker-compose -f docker-compose.prod.yml exec -T redis redis-cli -a ${REDIS_PASSWORD:-redis} --rdb "${BACKUP_DIR}/redis/dump.rdb"; then
    echo -e "${GREEN}Redis backup completed${NC}"
else
    echo -e "${YELLOW}Redis RDB backup failed, trying to copy existing dump...${NC}"
    docker-compose -f docker-compose.prod.yml exec -T redis sh -c "cat /data/dump.rdb" > "${BACKUP_DIR}/redis/dump.rdb"
    echo -e "${GREEN}Redis backup copied${NC}"
fi

# Backup uploaded files
echo -e "${GREEN}Backing up uploaded files...${NC}"
if [ -d "./uploads" ]; then
    cp -r ./uploads "${BACKUP_DIR}/uploads"
    echo -e "${GREEN}Uploads backup completed${NC}"
else
    echo -e "${YELLOW}No uploads directory found${NC}"
fi

# Backup logs
echo -e "${GREEN}Backing up logs...${NC}"
if [ -d "./logs" ]; then
    cp -r ./logs "${BACKUP_DIR}/logs"
    echo -e "${GREEN}Logs backup completed${NC}"
else
    echo -e "${YELLOW}No logs directory found${NC}"
fi

# Backup certificates
echo -e "${GREEN}Backing up SSL certificates...${NC}"
if [ -d "./certs" ]; then
    cp -r ./certs "${BACKUP_DIR}/certs"
    echo -e "${GREEN}Certificates backup completed${NC}"
else
    echo -e "${YELLOW}No certificates directory found${NC}"
fi

# Create a restore script in the backup directory
cat > "${BACKUP_DIR}/restore.sh" << 'EOL'
#!/bin/bash
set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting restoration of backup...${NC}"

# Restore database
echo -e "${GREEN}Restoring PostgreSQL database...${NC}"
docker-compose -f ../docker-compose.prod.yml exec -T postgres psql -U ${DB_USER:-postgres} -d ${DB_NAME:-onyxchat} -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
cat database_backup.sql | docker-compose -f ../docker-compose.prod.yml exec -T postgres psql -U ${DB_USER:-postgres} -d ${DB_NAME:-onyxchat}
echo -e "${GREEN}Database restored${NC}"

# Restore Redis data if needed
if [ -f "./redis/dump.rdb" ]; then
    echo -e "${GREEN}Restoring Redis data...${NC}"
    docker-compose -f ../docker-compose.prod.yml stop redis
    docker cp ./redis/dump.rdb $(docker-compose -f ../docker-compose.prod.yml ps -q redis):/data/dump.rdb
    docker-compose -f ../docker-compose.prod.yml start redis
    echo -e "${GREEN}Redis data restored${NC}"
fi

# Restore uploads directory if needed
if [ -d "./uploads" ]; then
    echo -e "${GREEN}Restoring uploads...${NC}"
    cp -r ./uploads/* ../uploads/
    echo -e "${GREEN}Uploads restored${NC}"
fi

echo -e "${GREEN}Restoration complete!${NC}"
EOL

chmod +x "${BACKUP_DIR}/restore.sh"

echo -e "${GREEN}Backup completed successfully at ${BACKUP_DIR}${NC}"
echo -e "${YELLOW}To restore this backup, run: ${BACKUP_DIR}/restore.sh${NC}" 