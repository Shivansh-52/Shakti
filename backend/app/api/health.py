from fastapi import APIRouter

router = APIRouter()

@router.get("/", tags=["health"]) 
async def uptime_health():
    """Simple health check endpoint for uptime monitoring services.
    Returns a minimal JSON response indicating the service is alive.
    """
    return {"status": "ok"}
