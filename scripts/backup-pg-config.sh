#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting PostgreSQL Configuration Backup${NC}"

# Create timestamp and backup directory
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="./backups/pg_config_${TIMESTAMP}"
mkdir -p "${BACKUP_DIR}"

echo -e "${GREEN}Created backup directory: ${BACKUP_DIR}${NC}"

# Get container ID for postgres
POSTGRES_CONTAINER=$(sudo docker ps | grep onyxchat-postgres | awk '{print $1}')

if [ -z "$POSTGRES_CONTAINER" ]; then
  echo -e "${YELLOW}PostgreSQL container not found or not running.${NC}"
  echo -e "${YELLOW}Cannot backup configuration.${NC}"
  exit 0
fi

echo -e "${GREEN}Found PostgreSQL container: ${POSTGRES_CONTAINER}${NC}"

# Backup PostgreSQL configuration
echo -e "${GREEN}Backing up PostgreSQL configuration...${NC}"

# Export PostgreSQL version
PG_VERSION=$(sudo docker exec $POSTGRES_CONTAINER postgres --version | awk '{print $3}')
echo $PG_VERSION > "${BACKUP_DIR}/postgres_version.txt"
echo -e "${GREEN}PostgreSQL version: ${PG_VERSION}${NC}"

# Export configuration files
sudo docker exec $POSTGRES_CONTAINER cat /var/lib/postgresql/data/postgresql.conf > "${BACKUP_DIR}/postgresql.conf"
sudo docker exec $POSTGRES_CONTAINER cat /var/lib/postgresql/data/pg_hba.conf > "${BACKUP_DIR}/pg_hba.conf"

# Backup PostgreSQL settings
echo -e "${GREEN}Exporting PostgreSQL settings...${NC}"
sudo docker exec $POSTGRES_CONTAINER psql -U postgres -c "SELECT name, setting FROM pg_settings;" > "${BACKUP_DIR}/pg_settings.txt"

# List extensions
echo -e "${GREEN}Exporting installed extensions...${NC}"
sudo docker exec $POSTGRES_CONTAINER psql -U postgres -c "SELECT * FROM pg_extension;" > "${BACKUP_DIR}/pg_extensions.txt"

# List schemas 
echo -e "${GREEN}Exporting database schemas...${NC}"
sudo docker exec $POSTGRES_CONTAINER psql -U postgres -c "SELECT schema_name FROM information_schema.schemata;" > "${BACKUP_DIR}/schemas.txt"

echo -e "${GREEN}PostgreSQL configuration backup completed successfully at: ${BACKUP_DIR}${NC}"

# Create a README file with restoration instructions
cat > "${BACKUP_DIR}/README.md" << EOF
# PostgreSQL Configuration Backup

This directory contains a backup of PostgreSQL configuration files and settings taken on $(date).

## Files

- \`postgresql.conf\`: Main PostgreSQL configuration file
- \`pg_hba.conf\`: Host-based authentication configuration
- \`pg_settings.txt\`: All PostgreSQL settings at backup time
- \`pg_extensions.txt\`: Installed extensions
- \`schemas.txt\`: Database schemas
- \`postgres_version.txt\`: PostgreSQL version

## Restoration

These files are for reference only. To restore configuration:

1. Stop the PostgreSQL container
2. Mount these files or copy them to the appropriate location
3. Start the PostgreSQL container

**Note:** Configuration restoration should be done carefully as it may cause compatibility issues.
EOF

echo -e "${GREEN}Created README with restoration instructions at ${BACKUP_DIR}/README.md${NC}" 