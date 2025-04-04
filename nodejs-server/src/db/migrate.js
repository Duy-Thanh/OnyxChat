require('dotenv').config();
const { Sequelize } = require('sequelize');
const path = require('path');
const initModels = require('../models/init-models');

// Parse command line arguments
const args = process.argv.slice(2);
const forceMode = args.includes('--force');

// Database connection parameters from environment variables
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: process.env.DB_PORT || 5433,
  database: process.env.DB_NAME || 'onyxchat',
  username: process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
};

console.log(`Starting database migration${forceMode ? ' in FORCE mode' : ''}...`);
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

// Migrate database schema
async function migrate() {
  try {
    // Test database connection
    await sequelize.authenticate();
    console.log('Database connection established successfully.');
    
    // Initialize models
    console.log('Initializing models...');
    const models = initModels(sequelize);
    
    // Sync all models with database
    console.log(`Syncing database schema${forceMode ? ' (force mode - will drop tables if they exist)' : ''}...`);
    await sequelize.sync({ force: forceMode });
    
    console.log('Database migration completed successfully!');
    process.exit(0);
  } catch (error) {
    console.error('Database migration failed:', error);
    process.exit(1);
  }
}

// Run migration
migrate(); 