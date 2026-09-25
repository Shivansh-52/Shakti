import uuid
from datetime import datetime, timezone, timedelta
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from app.schemas.auth import (
    UserCreate, UserLogin, Token, UserResponse, AuthResponse, RefreshRequest, LogoutRequest
)
from app.models.user import UserInDB, RefreshTokenInDB
from app.core.security import (
    hash_password, verify_password, create_access_token, create_refresh_token,
    decode_token, hash_token, get_current_user
)
from app.db.mongodb import get_database
from app.core.config import settings

router = APIRouter()

@router.post("/register", response_model=AuthResponse, status_code=status.HTTP_201_CREATED)
async def register(user_data: UserCreate):
    db = get_database()
    
    # Check duplicate email
    if await db.users.find_one({"email": user_data.email}):
        raise HTTPException(status_code=400, detail="Email already registered")
        
    # Check duplicate phone
    if await db.users.find_one({"phone": user_data.phone}):
        raise HTTPException(status_code=400, detail="Phone number already registered")
        
    user_id = str(uuid.uuid4())
    hashed_password = hash_password(user_data.password)
    
    now = datetime.now(timezone.utc)
    user = UserInDB(
        user_id=user_id,
        name=user_data.name,
        email=user_data.email,
        phone=user_data.phone,
        password_hash=hashed_password,
        role="USER",
        is_active=True,
        is_verified=False,
        created_at=now,
        updated_at=now,
        last_login_at=now
    )
    
    await db.users.insert_one(user.model_dump())
    
    # Tokens
    token_id = str(uuid.uuid4())
    access_token = create_access_token(subject=user_id, role=user.role)
    refresh_token = create_refresh_token(subject=user_id, token_id=token_id)
    
    refresh_expires_at = now + timedelta(days=settings.JWT_REFRESH_TOKEN_EXPIRE_DAYS)
    
    rt_doc = RefreshTokenInDB(
        token_id=token_id,
        user_id=user_id,
        token_hash=hash_token(refresh_token),
        created_at=now,
        expires_at=refresh_expires_at
    )
    await db.refresh_tokens.insert_one(rt_doc.model_dump())
    
    user_response = UserResponse(**user.model_dump())
    return AuthResponse(user=user_response, access_token=access_token, refresh_token=refresh_token)

@router.post("/login", response_model=AuthResponse)
async def login(user_data: UserLogin):
    db = get_database()
    
    user_doc = await db.users.find_one({"email": user_data.email})
    
    auth_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Incorrect email or password",
        headers={"WWW-Authenticate": "Bearer"},
    )
    
    if not user_doc or not verify_password(user_data.password, user_doc["password_hash"]):
        raise auth_exception
        
    if not user_doc.get("is_active", True):
        raise HTTPException(status_code=400, detail="Inactive user")
        
    user_id = user_doc["user_id"]
    now = datetime.now(timezone.utc)
    
    # Update last_login_at
    await db.users.update_one({"user_id": user_id}, {"$set": {"last_login_at": now}})
    
    # Tokens
    token_id = str(uuid.uuid4())
    access_token = create_access_token(subject=user_id, role=user_doc["role"])
    refresh_token = create_refresh_token(subject=user_id, token_id=token_id)
    
    refresh_expires_at = now + timedelta(days=settings.JWT_REFRESH_TOKEN_EXPIRE_DAYS)
    
    rt_doc = RefreshTokenInDB(
        token_id=token_id,
        user_id=user_id,
        token_hash=hash_token(refresh_token),
        created_at=now,
        expires_at=refresh_expires_at
    )
    await db.refresh_tokens.insert_one(rt_doc.model_dump())
    
    user_response = UserResponse(**user_doc)
    return AuthResponse(user=user_response, access_token=access_token, refresh_token=refresh_token)

@router.post("/refresh", response_model=Token)
async def refresh_token(request: RefreshRequest):
    payload = decode_token(request.refresh_token)
    auth_exception = HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")
    
    if payload is None or payload.get("type") != "refresh":
        raise auth_exception
        
    user_id = payload.get("sub")
    token_id = payload.get("jti")
    
    if not user_id or not token_id:
        raise auth_exception
        
    db = get_database()
    
    rt_doc = await db.refresh_tokens.find_one({"token_id": token_id})
    if not rt_doc or rt_doc.get("revoked_at") is not None:
        raise auth_exception
        
    # Verify hash
    if rt_doc["token_hash"] != hash_token(request.refresh_token):
        raise auth_exception
        
    user_doc = await db.users.find_one({"user_id": user_id})
    if not user_doc or not user_doc.get("is_active", True):
        raise auth_exception
        
    # Revoke old token
    now = datetime.now(timezone.utc)
    await db.refresh_tokens.update_one({"token_id": token_id}, {"$set": {"revoked_at": now}})
    
    # Issue new tokens
    new_token_id = str(uuid.uuid4())
    new_access_token = create_access_token(subject=user_id, role=user_doc["role"])
    new_refresh_token = create_refresh_token(subject=user_id, token_id=new_token_id)
    
    refresh_expires_at = now + timedelta(days=settings.JWT_REFRESH_TOKEN_EXPIRE_DAYS)
    
    new_rt_doc = RefreshTokenInDB(
        token_id=new_token_id,
        user_id=user_id,
        token_hash=hash_token(new_refresh_token),
        created_at=now,
        expires_at=refresh_expires_at
    )
    await db.refresh_tokens.insert_one(new_rt_doc.model_dump())
    
    return Token(access_token=new_access_token, refresh_token=new_refresh_token, token_type="bearer")

@router.post("/logout", status_code=status.HTTP_200_OK)
async def logout(request: LogoutRequest):
    payload = decode_token(request.refresh_token)
    if payload is None or payload.get("type") != "refresh":
        return {"detail": "Logged out successfully"}
        
    token_id = payload.get("jti")
    if token_id:
        db = get_database()
        now = datetime.now(timezone.utc)
        await db.refresh_tokens.update_one({"token_id": token_id}, {"$set": {"revoked_at": now}})
        
    return {"detail": "Logged out successfully"}

@router.get("/me", response_model=UserResponse)
async def get_me(current_user: dict = Depends(get_current_user)):
    return UserResponse(**current_user)
