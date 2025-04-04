require('dotenv').config();
const { Sequelize } = require('sequelize');
const bcrypt = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');
const path = require('path');
const initModels = require('../models/init-models');

// Database connection parameters from environment variables
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 5433,
  database: process.env.DB_NAME || 'onyxchat',
  username: process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
};

console.log('Starting database seeding...');
console.log(`Connecting to PostgreSQL at ${dbConfig.host}:${dbConfig.port}`);

// Create Sequelize instance
const sequelize = new Sequelize(
  dbConfig.database,
  dbConfig.username,
  dbConfig.password,
  {
    host: dbConfig.host,
    port: dbConfig.port,
    dialect: 'postgres',
    logging: console.log,
    pool: {
      max: 5,
      min: 0,
      acquire: 30000,
      idle: 10000
    }
  }
);

// Seed database with initial data
async function seed() {
  try {
    // Test database connection
    await sequelize.authenticate();
    console.log('Database connection established successfully.');
    
    // Initialize models
    const models = initModels(sequelize);
    const { User, Contact, Message } = models;
    
    // Check if users already exist
    const userCount = await User.count();
    if (userCount > 0) {
      console.log('Database already contains users. Skipping seed to prevent duplicates.');
      process.exit(0);
      return;
    }
    
    console.log('Starting to seed database with initial data...');
    
    // Create admin user
    const hashedPassword = await bcrypt.hash('admin123', 10);
    const adminId = uuidv4();
    const adminUser = await User.create({
      id: adminId,
      username: 'admin',
      email: 'admin@example.com',
      passwordHash: hashedPassword,
      displayName: 'Admin User',
      isActive: true,
      lastActiveAt: new Date()
    });
    console.log('Created admin user:', adminUser.username);
    
    // Create regular user
    const userId = uuidv4();
    const regularUser = await User.create({
      id: userId,
      username: 'user',
      email: 'user@example.com',
      passwordHash: hashedPassword,
      displayName: 'Regular User',
      isActive: true,
      lastActiveAt: new Date()
    });
    console.log('Created regular user:', regularUser.username);
    
    // Create test user
    const testUserId = uuidv4();
    const testUser = await User.create({
      id: testUserId,
      username: 'testuser',
      email: 'test@example.com',
      passwordHash: hashedPassword,
      displayName: 'Test User',
      isActive: true,
      lastActiveAt: new Date()
    });
    console.log('Created test user:', testUser.username);
    
    // Create contacts
    await Contact.create({
      id: uuidv4(),
      userId: adminId,
      contactId: userId,
      nickname: 'My Regular User'
    });
    
    await Contact.create({
      id: uuidv4(),
      userId: userId,
      contactId: adminId,
      nickname: 'My Admin'
    });
    
    await Contact.create({
      id: uuidv4(),
      userId: adminId,
      contactId: testUserId,
      nickname: 'Test Account'
    });
    
    await Contact.create({
      id: uuidv4(),
      userId: testUserId,
      contactId: adminId,
      nickname: 'Admin'
    });
    
    console.log('Created contact relationships');
    
    // Create sample messages
    const message1 = await Message.create({
      id: uuidv4(),
      senderId: adminId,
      recipientId: userId,
      content: 'Hello from Admin!',
      contentType: 'text',
      encrypted: false,
      sent: true,
      received: true,
      read: true,
      receivedAt: new Date(),
      readAt: new Date()
    });
    
    const message2 = await Message.create({
      id: uuidv4(),
      senderId: userId,
      recipientId: adminId,
      content: 'Hello Admin, how are you?',
      contentType: 'text',
      encrypted: false,
      sent: true,
      received: true,
      read: true,
      receivedAt: new Date(),
      readAt: new Date()
    });
    
    console.log('Created sample messages');
    console.log('Database seeding completed successfully!');
    process.exit(0);
  } catch (error) {
    console.error('Database seeding failed:', error);
    process.exit(1);
  }
}

// Run seed function
seed(); 