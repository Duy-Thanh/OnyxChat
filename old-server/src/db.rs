use sqlx::{Pool, Postgres};
use sqlx::postgres::PgPoolOptions;

use crate::error::{AppError, Result};

pub async fn connect(database_url: &str) -> Result<Pool<Postgres>> {
    PgPoolOptions::new()
        .max_connections(10)
        .connect(database_url)
        .await
        .map_err(|e| {
            tracing::error!("Failed to connect to database: {}", e);
            AppError::Database(e)
        })
}

pub async fn migrate(pool: &Pool<Postgres>) -> Result<()> {
    sqlx::migrate!("./migrations")
        .run(pool)
        .await
        .map_err(|e| {
            tracing::error!("Failed to run migrations: {}", e);
            AppError::internal(format!("Failed to run migrations: {}", e))
        })
}

// Prepare database with initial data (if needed)
pub async fn seed(_pool: &Pool<Postgres>) -> Result<()> {
    // Add any seeding operations here
    Ok(())
} 