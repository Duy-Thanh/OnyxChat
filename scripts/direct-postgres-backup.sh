#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting Direct PostgreSQL Backup${NC}"

# Create timestamp and backup directory
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="./backups/db_backup_${TIMESTAMP}"
mkdir -p "${BACKUP_DIR}"

echo -e "${GREEN}Created backup directory: ${BACKUP_DIR}${NC}"

# Get container ID for postgres
POSTGRES_CONTAINER=$(sudo docker ps | grep onyxchat-postgres | awk '{print $1}')

if [ -z "$POSTGRES_CONTAINER" ]; then
  echo -e "${YELLOW}PostgreSQL container not found or not running.${NC}"
  echo -e "${YELLOW}Skipping database backup.${NC}"
  exit 0
fi

echo -e "${GREEN}Found PostgreSQL container: ${POSTGRES_CONTAINER}${NC}"

# Get database details
DB_USER=${DB_USER:-postgres}
DB_NAME=${DB_NAME:-onyxchat}

echo -e "${GREEN}Backing up database ${DB_NAME} for user ${DB_USER}${NC}"

# Use direct container command to dump database
sudo docker exec $POSTGRES_CONTAINER pg_dump -U $DB_USER $DB_NAME > "${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql"

if [ $? -eq 0 ]; then
  echo -e "${GREEN}Database backup completed successfully at:${NC}"
  echo -e "${GREEN}${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql${NC}"
else
  echo -e "${RED}Database backup failed.${NC}"
  exit 1
fi

# Create a simple restore script
cat > "${BACKUP_DIR}/restore.sh" << EOF
#!/bin/bash
set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "\${YELLOW}Restoring PostgreSQL database from backup${NC}"

# Get container ID
POSTGRES_CONTAINER=\$(sudo docker ps | grep onyxchat-postgres | awk '{print \$1}')

if [ -z "\$POSTGRES_CONTAINER" ]; then
  echo -e "\${RED}PostgreSQL container not found or not running.${NC}"
  echo -e "\${RED}Start the containers first with: sudo docker-compose -f ../docker-compose.prod.yml up -d${NC}"
  exit 1
fi

# Restore database
echo -e "\${GREEN}Restoring to database ${DB_NAME} for user ${DB_USER}${NC}"
cat "${DB_NAME}_${TIMESTAMP}.sql" | sudo docker exec -i \$POSTGRES_CONTAINER psql -U ${DB_USER} -d ${DB_NAME}

echo -e "\${GREEN}Database restoration completed.${NC}"
EOF

chmod +x "${BACKUP_DIR}/restore.sh"

echo -e "${GREEN}Created restore script at ${BACKUP_DIR}/restore.sh${NC}" 