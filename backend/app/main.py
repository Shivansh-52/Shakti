from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.db.mongodb import connect_to_mongo, close_mongo_connection, db_manager
from app.api.auth import router as auth_router

@asynccontextmanager
async def lifespan(app: FastAPI):
    await connect_to_mongo()
    yield
    await close_mongo_connection()

app = FastAPI(
    title=settings.PROJECT_NAME,
    lifespan=lifespan
)

if settings.cors_origins_list:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

app.include_router(auth_router, prefix="/api/auth", tags=["auth"])

@app.get("/health", tags=["health"])
async def health_check():
    db_status = "disconnected"
    if db_manager.client is not None:
        try:
            await db_manager.client.server_info()
            db_status = "connected"
        except Exception:
            db_status = "error"
            
    return {
        "status": "ok",
        "database": db_status
    }
