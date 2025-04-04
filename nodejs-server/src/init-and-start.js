require('dotenv').config();
const migrate = require('./db/migrate');
const seed = require('./db/seed');

async function initAndStart() {
  console.log('Starting database initialization process...');
  
  try {
    // Run database migration
    console.log('Running database migration...');
    const migrationSuccess = await migrate();
    
    if (!migrationSuccess) {
      console.error('Migration failed. Starting server without database initialization.');
      require('./server');
      return;
    }
    
    // Run database seeding
    console.log('Running database seeding...');
    const seedingSuccess = await seed();
    
    if (!seedingSuccess) {
      console.error('Seeding failed. Starting server anyway.');
    } else {
      console.log('Database initialization completed successfully.');
    }
    
    // Start the server
    console.log('Starting server...');
    require('./server');
  } catch (error) {
    console.error('Error during initialization:', error);
    console.log('Starting server anyway...');
    require('./server');
  }
}

initAndStart(); 