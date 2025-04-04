#!/bin/sh
set -e

echo "Waiting for PostgreSQL to start..."
# Use a simple approach to wait for PostgreSQL
# Retry multiple times with longer wait between attempts
RETRIES=30
until [ $RETRIES -eq 0 ] || nc -z -v -w30 postgres 5432
do
  echo "Waiting for PostgreSQL to start... (retries left: $RETRIES)"
  RETRIES=$((RETRIES-1))
  sleep 3
done

if [ $RETRIES -eq 0 ]; then
  echo "Error: Failed to connect to PostgreSQL after multiple attempts"
  exit 1
fi

echo "PostgreSQL is up and running!"

echo "Running database migrations..."
node src/db/migrate.js || {
  echo "Warning: Database migration failed, but continuing..."
}

echo "Running database seed..."
node src/db/seed.js || {
  echo "Warning: Database seed failed, but continuing..."
}

echo "Starting OnyxChat server..."
exec node src/server.js 