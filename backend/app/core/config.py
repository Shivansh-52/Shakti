import os
from typing import List
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    PROJECT_NAME: str = "Suraksha Setu API"
    MONGODB_URI: str = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
    DATABASE_NAME: str = os.getenv("DATABASE_NAME", "suraksha_setu")
    
    JWT_SECRET: str = os.getenv("JWT_SECRET", "CHANGE_ME_IN_PRODUCTION")
    JWT_ACCESS_TOKEN_EXPIRE_MINUTES: int = int(os.getenv("JWT_ACCESS_TOKEN_EXPIRE_MINUTES", "15"))
    JWT_REFRESH_TOKEN_EXPIRE_DAYS: int = int(os.getenv("JWT_REFRESH_TOKEN_EXPIRE_DAYS", "30"))
    CORS_ORIGINS: str = os.getenv("CORS_ORIGINS", "")
    
    # Parse comma separated string into list if provided
    @property
    def cors_origins_list(self) -> List[str]:
        origins = os.getenv("CORS_ORIGINS", "")
        if not origins:
            return []
        return [origin.strip() for origin in origins.split(",") if origin.strip()]

    class Config:
        env_file = ".env"

settings = Settings()
