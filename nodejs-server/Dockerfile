FROM node:18-alpine

WORKDIR /app

# Install necessary tools including Python and build tools for bcrypt
RUN apk add --no-cache bash curl python3 make g++ gcc

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy application code
COPY . .

# Create a healthcheck script
RUN echo '#!/bin/sh\n\
curl -f http://localhost:8081/api/health || exit 1' > /app/healthcheck.sh && \
    chmod +x /app/healthcheck.sh

# Define healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 CMD /app/healthcheck.sh

EXPOSE 8081

# Use our initialization script
CMD ["node", "src/init-and-start.js"] 