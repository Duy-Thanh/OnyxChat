FROM node:22.14.0-alpine3.21

WORKDIR /app

# Install netcat to check if PostgreSQL is ready
RUN apk add --no-cache netcat-openbsd

# Copy package.json and package-lock.json
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy the rest of the application
COPY . .

# Create a directory for the database scripts
RUN mkdir -p /app/src/db

# Expose the port the app will run on
EXPOSE 8081

# Add a startup script to wait for PostgreSQL and then run migrations
COPY docker-entrypoint.sh /
RUN chmod +x /docker-entrypoint.sh

# Set the entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"] 