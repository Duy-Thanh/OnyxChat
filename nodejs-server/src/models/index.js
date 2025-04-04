const { Sequelize } = require('sequelize');
const fs = require('fs');
const path = require('path');
const initModels = require('./init-models');
const mockDb = require('./mock');
const basename = path.basename(__filename);

// Determine if we're in mock mode
const isMockMode = process.env.NODE_ENV === 'test' || process.env.USE_MOCK_DB === 'true';

// Initialize db object
const db = {};

console.log(`Node environment: ${process.env.NODE_ENV}`);
console.log(`Mock DB flag: ${process.env.USE_MOCK_DB}`);
console.log(`Using mock mode: ${isMockMode}`);

// Handle mock mode first before trying to connect to the database
if (isMockMode) {
  console.log('Running in mock database mode');
  Object.assign(db, mockDb);
  
  // We still need Sequelize for operators
  db.Sequelize = Sequelize;
} else {
  console.log('Connecting to PostgreSQL database');
  
  // Only create Sequelize instance if not in mock mode
  const sequelize = new Sequelize(
    process.env.DB_NAME,
    process.env.DB_USER,
    process.env.DB_PASSWORD,
    {
      host: process.env.DB_HOST,
      port: process.env.DB_PORT,
      dialect: 'postgres',
      logging: process.env.NODE_ENV === 'development' ? console.log : false,
      pool: {
        max: 5,
        min: 0,
        acquire: 30000,
        idle: 10000
      }
    }
  );

  // Initialize models
  const models = initModels(sequelize);
  
  // Add models to db
  Object.keys(models).forEach(modelName => {
    db[modelName] = models[modelName];
  });

  // Add sequelize instance to db
  db.sequelize = sequelize;
  db.Sequelize = Sequelize;
}

module.exports = db; 