import uuid
import time
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.app.core.database import get_db
from backend.app.core.security import get_password_hash
from backend.app.models.users import User
from backend.app.models.stores import Store
from backend.app.schemas.auth import Token, LoginRequest
from backend.app.schemas.users import UserResponse, UserCreate
from backend.app.services.auth_service import authenticate_user, get_current_user

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/login", response_model=Token)
async def login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    session: AsyncSession = Depends(get_db)
):
    login_data = LoginRequest(username=form_data.username, password=form_data.password)
    return await authenticate_user(session, login_data)


@router.post("/login/json", response_model=Token)
async def login_json(
    login_data: LoginRequest,
    session: AsyncSession = Depends(get_db)
):
    return await authenticate_user(session, login_data)


@router.get("/me", response_model=UserResponse)
async def get_me(current_user: User = Depends(get_current_user)):
    return current_user


@router.post("/register", response_model=UserResponse)
async def register_owner(
    user_in: UserCreate,
    session: AsyncSession = Depends(get_db)
):
    # Cek apakah username sudah dipakai
    q = select(User).where(User.username == user_in.username)
    res = await session.execute(q)
    if res.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Username sudah digunakan.")

    now = int(time.time() * 1000)
    user_id = str(uuid.uuid4())
    store_id = str(uuid.uuid4())

    # Buat Store baru
    store = Store(
        id=store_id,
        name=f"Toko {user_in.full_name}",
        owner_id=user_id,
        created_at=now,
        updated_at=now
    )
    session.add(store)

    user = User(
        id=user_id,
        store_id=store_id,
        username=user_in.username,
        password_hash=get_password_hash(user_in.password),
        full_name=user_in.full_name,
        role="OWNER",
        is_active=True,
        created_at=now
    )
    session.add(user)
    await session.commit()
    await session.refresh(user)
    return user
