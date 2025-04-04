#!/bin/sh
set -e

echo "Waiting for PostgreSQL to start..."
# Use a simple approach to wait for PostgreSQL
until nc -z -v -w30 postgres 5432
do
  echo "Waiting for PostgreSQL to start... (it's normal if this takes a minute)"
  sleep 2
done
echo "PostgreSQL is up and running!"

echo "Running database migrations..."
node src/db/migrate.js

echo "Running database seed..."
node src/db/seed.js

echo "Starting OnyxChat server..."
exec node src/server.js 