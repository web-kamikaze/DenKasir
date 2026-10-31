from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException, status, Depends
from backend.app.core.database import get_db
from backend.app.core.security import verify_password, get_password_hash, create_access_token, oauth2_scheme, decode_access_token
from backend.app.models.users import User
from backend.app.models.stores import Store
from backend.app.schemas.auth import LoginRequest, Token


async def authenticate_user(session: AsyncSession, login_data: LoginRequest) -> Token:
    query = select(User).where(User.username == login_data.username, User.is_active == True)
    result = await session.execute(query)
    user = result.scalar_one_or_none()

    if not user or not verify_password(login_data.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Username atau kata sandi tidak cocok.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Ambil info store
    store_query = select(Store).where(Store.id == user.store_id)
    store_res = await session.execute(store_query)
    store = store_res.scalar_one_or_none()
    store_name = store.name if store else "DenKasir UMKM"

    access_token = create_access_token(
        subject=user.id,
        role=user.role,
        store_id=user.store_id
    )

    return Token(
        access_token=access_token,
        token_type="bearer",
        user_id=user.id,
        username=user.username,
        full_name=user.full_name,
        role=user.role,
        store_id=user.store_id,
        store_name=store_name
    )


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    session: AsyncSession = Depends(get_db)
) -> User:
    payload = decode_access_token(token)
    user_id: str = payload.get("sub")
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token tidak memiliki subject ID valid.")

    query = select(User).where(User.id == user_id, User.is_active == True)
    result = await session.execute(query)
    user = result.scalar_one_or_none()

    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Pengguna tidak ditemukan atau nonaktif.")
    return user


async def require_owner_role(current_user: User = Depends(get_current_user)) -> User:
    if current_user.role != "OWNER":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Akses ditolak: Fitur ini hanya dapat diakses oleh akun OWNER/Pemilik Toko."
        )
    return current_user
