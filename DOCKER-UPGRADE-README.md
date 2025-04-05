# Docker Image Upgrade Guide

This guide explains how to upgrade the Docker images used in OnyxChat to the latest Alpine-based versions to fix CVEs and security vulnerabilities.

## Scripts Overview

These scripts will help you safely upgrade your Docker images:

1. **backup-before-upgrade.sh**: Creates a comprehensive backup of your database, Redis data, uploads, and configuration
2. **update-docker-images.sh**: Updates Docker image references in configuration files to the latest Alpine versions
3. **upgrade-docker-safe.sh**: A comprehensive script that performs all the steps (backup, update, restart) with error handling

## Quick Start

For a completely guided upgrade with automatic backup and recovery options:

```bash
sudo ./upgrade-docker-safe.sh
```

This will:
1. Back up all your data
2. Update Docker image versions in configuration files
3. Stop current containers
4. Pull latest images and rebuild custom ones
5. Start updated containers
6. Verify services are healthy
7. Show logs and status

## Manual Process

If you prefer to run the steps manually:

### 1. Create a Backup

```bash
./backup-before-upgrade.sh
```

This creates a backup in `./backups/upgrade_TIMESTAMP/` with its own restore script.

### 2. Update Docker Image References

```bash
./update-docker-images.sh
```

This updates the Docker image references in all configuration files to the latest Alpine versions.

### 3. Apply the Changes

```bash
sudo docker-compose -f docker-compose.prod.yml down
sudo docker-compose -f docker-compose.prod.yml pull
sudo docker-compose -f docker-compose.prod.yml build
sudo docker-compose -f docker-compose.prod.yml up -d
```

### 4. Restore from Backup (If Needed)

If anything goes wrong, you can restore from backup:

```bash
cd ./backups/upgrade_TIMESTAMP/
./restore.sh
```

## Updated Docker Images

The scripts will update:

- PostgreSQL: from 14-alpine to 16-alpine
- Redis: from 7-alpine to 7.2-alpine
- Nginx: ensuring latest mainline-alpine
- Node.js: ensuring latest 20-alpine

## Verifying the Upgrade

After upgrading, you can verify everything is working correctly with:

```bash
sudo docker-compose -f docker-compose.prod.yml ps
sudo docker-compose -f docker-compose.prod.yml logs
```

## Troubleshooting

If any issues occur during the upgrade:

1. Check the logs: `sudo docker-compose -f docker-compose.prod.yml logs`
2. Verify services are running: `sudo docker-compose -f docker-compose.prod.yml ps`
3. Restore from backup if needed: `cd ./backups/upgrade_TIMESTAMP/ && ./restore.sh`

For any persistent issues, check for PostgreSQL compatibility changes between versions 14 and 16, or other database migration issues that might need manual intervention. 