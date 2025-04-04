require('dotenv').config();
const { Sequelize } = require('sequelize');
const { v4: uuidv4 } = require('uuid');
const bcrypt = require('bcryptjs');
const initModels = require('../models/init-models');

console.log(`Connecting to database at ${process.env.DB_HOST}:${process.env.DB_PORT}`);

const sequelize = new Sequelize(
  process.env.DB_NAME,
  process.env.DB_USER,
  process.env.DB_PASSWORD,
  {
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    dialect: 'postgres',
    logging: true,
    retry: {
      max: 10,
      match: [
        /ConnectionRefusedError/,
        /SequelizeConnectionRefusedError/,
        /SequelizeConnectionError/,
        /ECONNREFUSED/,
        /ETIMEDOUT/,
      ],
      backoffBase: 1000,
      backoffExponent: 1.5,
    }
  }
);

async function seed() {
  try {
    // Retry logic for Docker environment
    const maxRetries = 30;
    const retryInterval = 2000;
    let retries = 0;
    let connected = false;

    while (!connected && retries < maxRetries) {
      try {
        console.log(`Connection attempt ${retries + 1}/${maxRetries}...`);
        await sequelize.authenticate();
        connected = true;
        console.log('Connection established successfully.');
      } catch (error) {
        retries++;
        if (retries >= maxRetries) {
          throw new Error(`Could not connect to database after ${maxRetries} attempts: ${error.message}`);
        }
        console.log(`Connection failed: ${error.message}. Retrying in ${retryInterval/1000} seconds...`);
        await new Promise(resolve => setTimeout(resolve, retryInterval));
      }
    }

    console.log('Initializing models...');
    const models = initModels(sequelize);
    const { User, Message } = models;

    // Only proceed if there are users already (created by migrate.js)
    const adminUser = await User.findOne({ where: { username: 'admin' } });
    const regularUser = await User.findOne({ where: { username: 'user' } });

    if (!adminUser || !regularUser) {
      console.error('Admin or regular user not found. Run migrate.js first.');
      return false;
    }

    console.log('Checking for existing messages...');
    const messageCount = await Message.count();
    
    if (messageCount > 0) {
      console.log(`Found ${messageCount} existing messages. Skipping seeding.`);
      return true;
    }

    console.log('Creating sample messages...');
    const messages = [
      {
        id: uuidv4(),
        senderId: adminUser.id,
        recipientId: regularUser.id,
        content: 'Hello, this is a test message from admin to user!',
        contentType: 'text',
        encrypted: false,
        sent: true,
        received: true,
        read: true,
        deleted: false,
        receivedAt: new Date(),
        readAt: new Date(),
        createdAt: new Date(Date.now() - 3600000),
        updatedAt: new Date(Date.now() - 3600000)
      },
      {
        id: uuidv4(),
        senderId: regularUser.id,
        recipientId: adminUser.id,
        content: 'Hi admin, got your message!',
        contentType: 'text',
        encrypted: false,
        sent: true,
        received: true,
        read: true,
        deleted: false,
        receivedAt: new Date(),
        readAt: new Date(),
        createdAt: new Date(Date.now() - 3000000),
        updatedAt: new Date(Date.now() - 3000000)
      },
      {
        id: uuidv4(),
        senderId: adminUser.id,
        recipientId: regularUser.id,
        content: 'Great! How are you doing?',
        contentType: 'text',
        encrypted: false,
        sent: true,
        received: true,
        read: false,
        deleted: false,
        receivedAt: new Date(),
        createdAt: new Date(Date.now() - 2400000),
        updatedAt: new Date(Date.now() - 2400000)
      },
      {
        id: uuidv4(),
        senderId: regularUser.id,
        recipientId: adminUser.id,
        content: 'I\'m doing well! Just testing this chat app.',
        contentType: 'text',
        encrypted: false,
        sent: true,
        received: false,
        read: false,
        deleted: false,
        createdAt: new Date(Date.now() - 1800000),
        updatedAt: new Date(Date.now() - 1800000)
      }
    ];

    await Message.bulkCreate(messages);
    console.log(`Created ${messages.length} sample messages.`);

    console.log('Seeding completed successfully.');
    return true;
  } catch (error) {
    console.error('Error during seeding:', error);
    return false;
  }
}

// Allow seeding to be run both from command line and as a module
if (require.main === module) {
  seed().then(success => {
    process.exit(success ? 0 : 1);
  });
} else {
  module.exports = seed;
} 