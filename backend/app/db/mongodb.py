import logging
from motor.motor_asyncio import AsyncIOMotorClient
from app.core.config import settings

logger = logging.getLogger(__name__)

class Database:
    client: AsyncIOMotorClient = None
    db = None

db_manager = Database()

async def connect_to_mongo():
    logger.info("Connecting to MongoDB...")
    try:
        db_manager.client = AsyncIOMotorClient(settings.MONGODB_URI)
        db_manager.db = db_manager.client[settings.DATABASE_NAME]
        
        # Verify connection
        await db_manager.client.server_info()
        logger.info("Successfully connected to MongoDB.")
        
        # Create indexes
        await setup_indexes(db_manager.db)
        
    except Exception as e:
        logger.error(f"Failed to connect to MongoDB: {e}")
        raise

async def close_mongo_connection():
    logger.info("Closing MongoDB connection...")
    if db_manager.client:
        db_manager.client.close()
        logger.info("MongoDB connection closed.")

async def setup_indexes(db):
    # Unique index on user_id
    await db.users.create_index("user_id", unique=True)
    # Unique index on email
    await db.users.create_index("email", unique=True)
    # Unique index on phone
    await db.users.create_index("phone", unique=True, sparse=True)
    
    # Refresh token index
    await db.refresh_tokens.create_index("token_id", unique=True)
    await db.refresh_tokens.create_index("user_id")

def get_database():
    if db_manager.db is None:
        raise Exception("Database not initialized")
    return db_manager.db
