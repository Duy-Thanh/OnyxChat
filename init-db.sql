-- Drop existing schema if it exists
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

-- Set up privileges
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;

-- Create users table
CREATE TABLE IF NOT EXISTS "users" (
    "id" UUID PRIMARY KEY,
    "username" VARCHAR(255) NOT NULL UNIQUE,
    "email" VARCHAR(255) NOT NULL UNIQUE,
    "passwordHash" VARCHAR(255) NOT NULL,
    "displayName" VARCHAR(255) NOT NULL,
    "isActive" BOOLEAN DEFAULT FALSE,
    "lastActiveAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create messages table
CREATE TABLE IF NOT EXISTS "messages" (
    "id" UUID PRIMARY KEY,
    "senderId" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "recipientId" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "content" TEXT NOT NULL,
    "contentType" VARCHAR(255) DEFAULT 'text',
    "encrypted" BOOLEAN DEFAULT TRUE,
    "sent" BOOLEAN DEFAULT TRUE,
    "received" BOOLEAN DEFAULT FALSE,
    "read" BOOLEAN DEFAULT FALSE,
    "deleted" BOOLEAN DEFAULT FALSE,
    "receivedAt" TIMESTAMP WITH TIME ZONE,
    "readAt" TIMESTAMP WITH TIME ZONE,
    "deletedAt" TIMESTAMP WITH TIME ZONE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create refresh_tokens table
CREATE TABLE IF NOT EXISTS "refresh_tokens" (
    "id" UUID PRIMARY KEY,
    "token" VARCHAR(255) NOT NULL UNIQUE,
    "userId" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    "revoked" BOOLEAN DEFAULT FALSE,
    "revokedAt" TIMESTAMP WITH TIME ZONE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create contacts table 
CREATE TABLE IF NOT EXISTS "contacts" (
    "id" UUID PRIMARY KEY,
    "userId" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "contactId" UUID NOT NULL REFERENCES "users"("id") ON DELETE NO ACTION,
    "nickname" VARCHAR(255),
    "blocked" BOOLEAN DEFAULT FALSE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE("userId", "contactId")
);

-- Create user_keys table for cryptographic keys
CREATE TABLE IF NOT EXISTS "user_keys" (
    "id" UUID PRIMARY KEY,
    "userId" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "publicKey" TEXT NOT NULL,
    "privateKey" TEXT,
    "active" BOOLEAN DEFAULT TRUE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create one_time_prekeys table
CREATE TABLE IF NOT EXISTS "one_time_prekeys" (
    "id" UUID PRIMARY KEY,
    "userId" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "keyId" INTEGER NOT NULL,
    "key" TEXT NOT NULL,
    "used" BOOLEAN DEFAULT FALSE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE("userId", "keyId")
);

-- Create sessions table
CREATE TABLE IF NOT EXISTS "sessions" (
    "id" UUID PRIMARY KEY,
    "userId" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "otherUserId" UUID NOT NULL REFERENCES "users"("id") ON DELETE NO ACTION,
    "sessionData" TEXT NOT NULL,
    "active" BOOLEAN DEFAULT TRUE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE("userId", "otherUserId")
);

-- Insert test user
INSERT INTO "users" ("id", "username", "email", "passwordHash", "displayName", "isActive")
VALUES 
  ('c30d0e30-46b3-44b3-aa4a-a2057cf8793f', 'admin', 'admin@example.com', 
   '$2a$10$5RBFw3/g/Q5xQ5atSsBUnOBvBjZCKnUv/DL2R.5w62JU2tGqzJy2K', 'Admin User', TRUE)
ON CONFLICT ("username") DO NOTHING; 

-- Insert default users
INSERT INTO "users" ("id", "username", "email", "passwordHash", "displayName", "isActive", "lastActiveAt", "createdAt", "updatedAt")
VALUES 
  ('11111111-1111-1111-1111-111111111111', 'admin', 'admin@example.com', '$2a$10$xVlbqLV5nFXQ/6BTJOwBEuRlCgS9oT3ayRfaFcFj7xj7reVIBvrhK', 'Admin User', true, NOW(), NOW(), NOW()),
  ('22222222-2222-2222-2222-222222222222', 'user', 'user@example.com', '$2a$10$xVlbqLV5nFXQ/6BTJOwBEuRlCgS9oT3ayRfaFcFj7xj7reVIBvrhK', 'Regular User', true, NOW(), NOW(), NOW()),
  ('33333333-3333-3333-3333-333333333333', 'testuser', 'test@example.com', '$2a$10$xVlbqLV5nFXQ/6BTJOwBEuRlCgS9oT3ayRfaFcFj7xj7reVIBvrhK', 'Test User', true, NOW(), NOW(), NOW());

-- Insert contacts
INSERT INTO "contacts" ("id", "userId", "contactId", "nickname", "createdAt", "updatedAt")
VALUES 
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'My Regular User', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'My Admin', NOW(), NOW()),
  ('cccccccc-cccc-cccc-cccc-cccccccccccc', '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', 'Test Account', NOW(), NOW()),
  ('dddddddd-dddd-dddd-dddd-dddddddddddd', '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Admin', NOW(), NOW());

-- Insert sample messages
INSERT INTO "messages" ("id", "senderId", "recipientId", "content", "contentType", "encrypted", "sent", "received", "read", "receivedAt", "readAt", "createdAt", "updatedAt")
VALUES 
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 'Hello from Admin!', 'text', false, true, true, true, NOW(), NOW(), NOW(), NOW()),
  ('ffffffff-ffff-ffff-ffff-ffffffffffff', '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Hello Admin, how are you?', 'text', false, true, true, true, NOW(), NOW(), NOW(), NOW()); 