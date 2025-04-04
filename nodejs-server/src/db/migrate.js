require('dotenv').config();
const { Sequelize } = require('sequelize');
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

async function migrate() {
  try {
    console.log('Connecting to database...');
    await sequelize.authenticate();
    console.log('Connection established successfully.');

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
        const bcrypt = require('bcrypt');
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
    process.exit(0);
  } catch (error) {
    console.error('Error during migration:', error);
    process.exit(1);
  }
}

migrate(); 