import hashlib
from datetime import datetime, timedelta, timezone
from typing import Any, Union, Optional
import bcrypt
import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer

from app.core.config import settings
from app.db.mongodb import get_database

ALGORITHM = "HS256"
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/login")

def hash_password(password: str) -> str:
    pwd_bytes = password.encode('utf-8')
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(pwd_bytes, salt).decode('utf-8')

def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        pwd_bytes = plain_password.encode('utf-8')
        hash_bytes = hashed_password.encode('utf-8')
        return bcrypt.checkpw(pwd_bytes, hash_bytes)
    except Exception:
        return False

def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()

def create_access_token(subject: Union[str, Any], role: str, expires_delta: Optional[timedelta] = None) -> str:
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(minutes=settings.JWT_ACCESS_TOKEN_EXPIRE_MINUTES)
    
    to_encode = {"exp": expire, "sub": str(subject), "role": role, "type": "access"}
    encoded_jwt = jwt.encode(to_encode, settings.JWT_SECRET, algorithm=ALGORITHM)
    return encoded_jwt

def create_refresh_token(subject: Union[str, Any], token_id: str, expires_delta: Optional[timedelta] = None) -> str:
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(days=settings.JWT_REFRESH_TOKEN_EXPIRE_DAYS)
        
    to_encode = {"exp": expire, "sub": str(subject), "jti": token_id, "type": "refresh"}
    encoded_jwt = jwt.encode(to_encode, settings.JWT_SECRET, algorithm=ALGORITHM)
    return encoded_jwt

def decode_token(token: str) -> Optional[dict]:
    try:
        decoded_token = jwt.decode(token, settings.JWT_SECRET, algorithms=[ALGORITHM])
        return decoded_token
    except jwt.ExpiredSignatureError:
        return None
    except jwt.InvalidTokenError:
        return None

async def get_current_user(token: str = Depends(oauth2_scheme)):
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    payload = decode_token(token)
    if payload is None or payload.get("type") != "access":
        raise credentials_exception
        
    user_id: str = payload.get("sub")
    if user_id is None:
        raise credentials_exception
        
    db = get_database()
    user_doc = await db.users.find_one({"user_id": user_id})
    if user_doc is None:
        raise credentials_exception
        
    return user_doc

async def require_user(current_user: dict = Depends(get_current_user)):
    if not current_user.get("is_active"):
        raise HTTPException(status_code=400, detail="Inactive user")
    return current_user

async def require_admin(current_user: dict = Depends(get_current_user)):
    if not current_user.get("is_active"):
        raise HTTPException(status_code=400, detail="Inactive user")
    if current_user.get("role") != "ADMIN":
        raise HTTPException(status_code=403, detail="Not enough permissions")
    return current_user
