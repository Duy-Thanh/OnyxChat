# OnyxChat Server Maintenance Scripts

This directory contains scripts for managing, monitoring, and maintaining the OnyxChat server infrastructure.

## Available Scripts

### Docker Management

- **upgrade-docker-safe.sh**: Safely upgrades Docker containers while backing up critical data
- **update-docker-images.sh**: Updates Docker image references in compose files
- **direct-postgres-backup.sh**: Creates a direct backup of PostgreSQL data without checking container health
- **backup-pg-config.sh**: Backs up PostgreSQL configuration files and settings
- **container-monitoring.sh**: Monitors Docker container health, logs, and resource usage

## How to Use

### Monitoring

To check the status of all OnyxChat containers:

```bash
./container-monitoring.sh
```

This will display container status, resource usage, recent logs, and service health.

### Upgrades

To safely upgrade Docker containers with data backup:

```bash
sudo ./upgrade-docker-safe.sh
```

This script will:
1. Back up all PostgreSQL data
2. Save configuration files
3. Update Docker images to newer versions
4. Restart services
5. Verify services are healthy

### Backups

For a direct database backup regardless of container health:

```bash
./direct-postgres-backup.sh
```

For PostgreSQL configuration backup only:

```bash
./backup-pg-config.sh
```

## Troubleshooting

If services fail to start after an upgrade:

1. Check logs using `./container-monitoring.sh`
2. Restore from backup using the generated restore script in the backup directory
3. Verify that all services are compatible with the versions in the docker-compose files

## Notes

- All scripts require Docker to be installed and running
- Some scripts may require sudo privileges
- Backups are stored in the ./backups directory with timestamps
- Each backup includes its own restore instructions 