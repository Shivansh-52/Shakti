import asyncio
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.db.mongodb import connect_to_mongo, close_mongo_connection, db_manager
from app.api.auth import router as auth_router
from app.api.safe_route import router as safe_route_router
from app.api.health import router as health_router
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await connect_to_mongo()
    # Pre-load the ML model in a thread so the event loop isn't blocked
    try:
        loop = asyncio.get_event_loop()
        from app.ml.model_loader import load_models
        await loop.run_in_executor(None, load_models)
        logger.info("Safe route ML model pre-loaded.")
    except Exception as e:
        logger.warning(f"Could not pre-load ML model: {e}")
    yield
    await close_mongo_connection()

app = FastAPI(
    title=settings.PROJECT_NAME,
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/", tags=["root"])\nasync def root():\n    return {"status": "running"}\n\napp.include_router(auth_router, prefix="/api/auth", tags=["auth"])
app.include_router(safe_route_router, prefix="/api/safe-route", tags=["safe-route"])

from app.api.buddy_ws import router as buddy_router
app.include_router(buddy_router, tags=["buddy"])

from app.api.emergency import router as emergency_router
app.include_router(emergency_router, prefix="/api/emergency", tags=["emergency"])
app.include_router(health_router, prefix="/health", tags=["health"])

import os
from fastapi.staticfiles import StaticFiles
os.makedirs("uploads/sos_videos", exist_ok=True)
app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")
@app.get("/ping", tags=["health"])
async def ping():
    return {"status": "ok"}
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
