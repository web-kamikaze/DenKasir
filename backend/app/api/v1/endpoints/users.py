import uuid
import time
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.app.core.database import get_db
from backend.app.core.security import get_password_hash
from backend.app.models.users import User
from backend.app.schemas.users import UserResponse, UserCreate, UserUpdate
from backend.app.services.auth_service import get_current_user, require_owner_role

router = APIRouter(prefix="/users", tags=["Users"])


@router.get("", response_model=List[UserResponse])
async def list_users(
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    query = select(User).where(User.store_id == current_user.store_id).order_by(User.created_at.desc())
    res = await session.execute(query)
    return list(res.scalars().all())


@router.post("", response_model=UserResponse)
async def create_user(
    user_in: UserCreate,
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    # Cek duplikasi username
    q = select(User).where(User.username == user_in.username)
    res = await session.execute(q)
    if res.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Username sudah digunakan.")

    now = int(time.time() * 1000)
    user = User(
        id=str(uuid.uuid4()),
        store_id=current_user.store_id,
        username=user_in.username,
        password_hash=get_password_hash(user_in.password),
        full_name=user_in.full_name,
        role=user_in.role or "KASIR",
        is_active=True,
        created_at=now
    )
    session.add(user)
    await session.commit()
    await session.refresh(user)
    return user


@router.delete("/{user_id}")
async def delete_user(
    user_id: str,
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    if user_id == current_user.id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Tidak dapat menghapus akun sendiri.")

    q = select(User).where(User.id == user_id, User.store_id == current_user.store_id)
    res = await session.execute(q)
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Pengguna tidak ditemukan.")

    await session.delete(user)
    await session.commit()
    return {"message": "Pengguna berhasil dihapus."}
