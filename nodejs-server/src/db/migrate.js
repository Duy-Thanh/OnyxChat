require('dotenv').config();
const { Sequelize } = require('sequelize');
const initModels = require('../models/init-models');
const bcrypt = require('bcryptjs');

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

async function migrate() {
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

    console.log('Syncing database...');
    // Force: true will drop tables if they exist
    // Use with caution in production!
    const force = process.argv.includes('--force');
    if (force) {
      console.log('WARNING: Forcing database sync. This will drop all tables!');
    }
    
    await sequelize.sync({ force });
    console.log('Database synchronized successfully.');

    // Create admin user if it doesn't exist
    if (!force) {
      console.log('Checking for admin user...');
      const { User } = models;
      const adminUser = await User.findOne({ where: { username: 'admin' } });
      
      if (!adminUser) {
        const salt = await bcrypt.genSalt(10);
        const passwordHash = await bcrypt.hash('password', salt);
        
        console.log('Creating admin user...');
        await User.create({
          username: 'admin',
          email: 'admin@example.com',
          passwordHash,
          displayName: 'Admin User',
          isActive: true,
          lastActiveAt: new Date()
        });
        
        console.log('Creating regular user...');
        await User.create({
          username: 'user',
          email: 'user@example.com',
          passwordHash,
          displayName: 'Regular User',
          isActive: true,
          lastActiveAt: new Date()
        });
        
        console.log('Users created successfully.');
      } else {
        console.log('Admin user already exists.');
      }
    }

    console.log('Migration completed successfully.');
    return true;
  } catch (error) {
    console.error('Error during migration:', error);
    return false;
  }
}

// Allow migration to be run both from command line and as a module
if (require.main === module) {
  migrate().then(success => {
    process.exit(success ? 0 : 1);
  });
} else {
  module.exports = migrate;
} 