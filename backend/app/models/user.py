from datetime import datetime, timezone
from typing import Optional
from pydantic import BaseModel, EmailStr, Field

def get_utc_now():
    return datetime.now(timezone.utc)

class UserInDB(BaseModel):
    user_id: str
    name: str
    email: EmailStr
    phone: str
    password_hash: str
    role: str = "USER"
    is_active: bool = True
    is_verified: bool = False
    created_at: datetime = Field(default_factory=get_utc_now)
    updated_at: datetime = Field(default_factory=get_utc_now)
    last_login_at: Optional[datetime] = None

class RefreshTokenInDB(BaseModel):
    token_id: str
    user_id: str
    token_hash: str
    created_at: datetime = Field(default_factory=get_utc_now)
    expires_at: datetime
    revoked_at: Optional[datetime] = None
