require('dotenv').config();
const { Sequelize } = require('sequelize');
const { v4: uuidv4 } = require('uuid');
const bcrypt = require('bcrypt');
const initModels = require('../models/init-models');

const sequelize = new Sequelize(
  process.env.DB_NAME,
  process.env.DB_USER,
  process.env.DB_PASSWORD,
  {
    host: process.env.DB_HOST,
    port: process.env.DB_PORT,
    dialect: 'postgres',
    logging: true
  }
);

async function seed() {
  try {
    console.log('Connecting to database...');
    await sequelize.authenticate();
    console.log('Connection established successfully.');

    console.log('Initializing models...');
    const models = initModels(sequelize);
    const { User, Message } = models;

    // Only proceed if there are users already (created by migrate.js)
    const adminUser = await User.findOne({ where: { username: 'admin' } });
    const regularUser = await User.findOne({ where: { username: 'user' } });

    if (!adminUser || !regularUser) {
      console.error('Admin or regular user not found. Run migrate.js first.');
      process.exit(1);
    }

    console.log('Checking for existing messages...');
    const messageCount = await Message.count();
    
    if (messageCount > 0) {
      console.log(`Found ${messageCount} existing messages. Skipping seeding.`);
      process.exit(0);
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
    process.exit(0);
  } catch (error) {
    console.error('Error during seeding:', error);
    process.exit(1);
  }
}

seed(); 