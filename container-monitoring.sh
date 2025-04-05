#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Check if docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker is not installed.${NC}"
    exit 1
fi

# Get the onyxchat project containers
echo -e "${BLUE}${BOLD}OnyxChat Container Status${NC}"
echo -e "============================"

# Get all containers related to OnyxChat
CONTAINERS=$(sudo docker ps -a --filter "name=onyxchat" --format "{{.Names}}")

# If no containers found
if [ -z "$CONTAINERS" ]; then
    echo -e "${YELLOW}No OnyxChat containers found.${NC}"
    exit 0
fi

# Get current time for uptime calculation
CURRENT_TIME=$(date +%s)

echo -e "${BLUE}${BOLD}Container Status:${NC}"
printf "%-30s %-10s %-20s %-20s %-10s\n" "CONTAINER" "STATUS" "UPTIME" "CPU %" "MEM USAGE"
echo "----------------------------------------------------------------------------------------"

for CONTAINER in $CONTAINERS; do
    # Get container info
    STATUS=$(sudo docker inspect --format='{{.State.Status}}' $CONTAINER)
    
    # Color based on status
    STATUS_COLOR="${GREEN}"
    if [ "$STATUS" != "running" ]; then
        STATUS_COLOR="${RED}"
    fi
    
    # Only get stats for running containers
    if [ "$STATUS" == "running" ]; then
        # Get container start time and calculate uptime
        START_TIME=$(sudo docker inspect --format='{{.State.StartedAt}}' $CONTAINER)
        START_UNIX=$(date -d "$START_TIME" +%s)
        UPTIME_SECONDS=$((CURRENT_TIME - START_UNIX))
        
        # Format uptime
        UPTIME_DAYS=$((UPTIME_SECONDS / 86400))
        UPTIME_HOURS=$(( (UPTIME_SECONDS % 86400) / 3600 ))
        UPTIME_MINS=$(( (UPTIME_SECONDS % 3600) / 60 ))
        
        if [ $UPTIME_DAYS -gt 0 ]; then
            UPTIME="${UPTIME_DAYS}d ${UPTIME_HOURS}h ${UPTIME_MINS}m"
        elif [ $UPTIME_HOURS -gt 0 ]; then
            UPTIME="${UPTIME_HOURS}h ${UPTIME_MINS}m"
        else
            UPTIME="${UPTIME_MINS}m"
        fi
        
        # Get CPU and memory usage
        STATS=$(sudo docker stats --no-stream $CONTAINER --format "{{.CPUPerc}}|{{.MemUsage}}")
        CPU_PERCENT=$(echo $STATS | cut -d'|' -f1)
        MEM_USAGE=$(echo $STATS | cut -d'|' -f2)
    else
        UPTIME="N/A"
        CPU_PERCENT="N/A"
        MEM_USAGE="N/A"
    fi
    
    printf "${STATUS_COLOR}%-30s${NC} %-10s %-20s %-20s %-10s\n" "$CONTAINER" "$STATUS" "$UPTIME" "$CPU_PERCENT" "$MEM_USAGE"
done

echo -e "\n${BLUE}${BOLD}Container Logs (last 5 entries):${NC}"
echo "============================"

for CONTAINER in $CONTAINERS; do
    if [ "$(sudo docker inspect --format='{{.State.Status}}' $CONTAINER)" == "running" ]; then
        echo -e "${BOLD}$CONTAINER:${NC}"
        sudo docker logs --tail 5 $CONTAINER 2>&1 | while read -r line; do
            echo "  $line"
        done
        echo ""
    fi
done

# Check for any container restarts in the last 24 hours
echo -e "${BLUE}${BOLD}Container Restarts (last 24 hours):${NC}"
echo "================================="

RESTART_COUNT=0
for CONTAINER in $CONTAINERS; do
    # Get restart count
    RESTARTS=$(sudo docker inspect --format='{{.RestartCount}}' $CONTAINER)
    if [ "$RESTARTS" -gt 0 ]; then
        echo -e "${YELLOW}$CONTAINER: $RESTARTS restarts${NC}"
        RESTART_COUNT=$((RESTART_COUNT + 1))
    fi
done

if [ "$RESTART_COUNT" -eq 0 ]; then
    echo -e "${GREEN}No container restarts detected.${NC}"
fi

# Health check status
echo -e "\n${BLUE}${BOLD}Service Health:${NC}"
echo "================="

# Check if PostgreSQL is responding
POSTGRES_CONTAINER=$(sudo docker ps --filter "name=onyxchat-postgres" --format "{{.Names}}" | head -1)
if [ -n "$POSTGRES_CONTAINER" ] && [ "$(sudo docker inspect --format='{{.State.Status}}' $POSTGRES_CONTAINER)" == "running" ]; then
    if sudo docker exec -i $POSTGRES_CONTAINER pg_isready -U postgres > /dev/null 2>&1; then
        echo -e "${GREEN}PostgreSQL: Healthy${NC}"
    else
        echo -e "${RED}PostgreSQL: Unhealthy${NC}"
    fi
else
    echo -e "${YELLOW}PostgreSQL: Not running${NC}"
fi

# Check if Redis is responding
REDIS_CONTAINER=$(sudo docker ps --filter "name=onyxchat-redis" --format "{{.Names}}" | head -1)
if [ -n "$REDIS_CONTAINER" ] && [ "$(sudo docker inspect --format='{{.State.Status}}' $REDIS_CONTAINER)" == "running" ]; then
    if sudo docker exec -i $REDIS_CONTAINER redis-cli PING | grep -q "PONG"; then
        echo -e "${GREEN}Redis: Healthy${NC}"
    else
        echo -e "${RED}Redis: Unhealthy${NC}"
    fi
else
    echo -e "${YELLOW}Redis: Not running${NC}"
fi

# Check if Nginx is responding
NGINX_CONTAINER=$(sudo docker ps --filter "name=onyxchat-nginx" --format "{{.Names}}" | head -1)
if [ -n "$NGINX_CONTAINER" ] && [ "$(sudo docker inspect --format='{{.State.Status}}' $NGINX_CONTAINER)" == "running" ]; then
    if curl -s http://localhost > /dev/null 2>&1; then
        echo -e "${GREEN}Nginx: Healthy${NC}"
    else
        echo -e "${RED}Nginx: Unhealthy${NC}"
    fi
else
    echo -e "${YELLOW}Nginx: Not running${NC}"
fi

# Check if Node.js server is responding
SERVER_CONTAINER=$(sudo docker ps --filter "name=onyxchat-server" --format "{{.Names}}" | head -1)
if [ -n "$SERVER_CONTAINER" ] && [ "$(sudo docker inspect --format='{{.State.Status}}' $SERVER_CONTAINER)" == "running" ]; then
    if curl -s http://localhost:3000/health > /dev/null 2>&1; then
        echo -e "${GREEN}Node.js Server: Healthy${NC}"
    else
        echo -e "${RED}Node.js Server: Unhealthy${NC}"
    fi
else
    echo -e "${YELLOW}Node.js Server: Not running${NC}"
fi

echo -e "\n${BLUE}${BOLD}Disk Space:${NC}"
echo "============="
df -h | grep -E '(Filesystem|/dev/sd|/$)'

echo -e "\n${BLUE}${BOLD}Docker System Status:${NC}"
echo "======================"
sudo docker system df

echo -e "\n${GREEN}Monitoring completed.${NC}" 