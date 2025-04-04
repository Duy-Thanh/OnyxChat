-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    "passwordHash" VARCHAR(255) NOT NULL,
    "displayName" VARCHAR(255) NOT NULL,
    "isActive" BOOLEAN DEFAULT FALSE,
    "lastActiveAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create messages table
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    "senderId" UUID NOT NULL REFERENCES users(id),
    "recipientId" UUID NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    "contentType" VARCHAR(50) DEFAULT 'text',
    encrypted BOOLEAN DEFAULT TRUE,
    sent BOOLEAN DEFAULT TRUE,
    received BOOLEAN DEFAULT FALSE,
    read BOOLEAN DEFAULT FALSE,
    deleted BOOLEAN DEFAULT FALSE,
    "receivedAt" TIMESTAMP WITH TIME ZONE,
    "readAt" TIMESTAMP WITH TIME ZONE,
    "deletedAt" TIMESTAMP WITH TIME ZONE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create refresh_tokens table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    "userId" UUID NOT NULL REFERENCES users(id),
    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    "revokedAt" TIMESTAMP WITH TIME ZONE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create contacts table 
CREATE TABLE IF NOT EXISTS contacts (
    id UUID PRIMARY KEY,
    "userId" UUID NOT NULL REFERENCES users(id),
    "contactId" UUID NOT NULL REFERENCES users(id),
    nickname VARCHAR(255),
    blocked BOOLEAN DEFAULT FALSE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE("userId", "contactId")
);

-- Create user_keys table for cryptographic keys
CREATE TABLE IF NOT EXISTS user_keys (
    id UUID PRIMARY KEY,
    "userId" UUID NOT NULL REFERENCES users(id),
    "publicKey" TEXT NOT NULL,
    "privateKey" TEXT,
    active BOOLEAN DEFAULT TRUE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create one_time_prekeys table
CREATE TABLE IF NOT EXISTS one_time_prekeys (
    id UUID PRIMARY KEY,
    "userId" UUID NOT NULL REFERENCES users(id),
    "keyId" INTEGER NOT NULL,
    key TEXT NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE("userId", "keyId")
);

-- Create sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY,
    "userId" UUID NOT NULL REFERENCES users(id),
    "otherUserId" UUID NOT NULL REFERENCES users(id),
    "sessionData" TEXT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE("userId", "otherUserId")
);

-- Insert test user
INSERT INTO users (id, username, email, "passwordHash", "displayName", "isActive")
VALUES 
  ('c30d0e30-46b3-44b3-aa4a-a2057cf8793f', 'admin', 'admin@example.com', 
   '$2a$10$5RBFw3/g/Q5xQ5atSsBUnOBvBjZCKnUv/DL2R.5w62JU2tGqzJy2K', 'Admin User', TRUE)
ON CONFLICT (username) DO NOTHING; 