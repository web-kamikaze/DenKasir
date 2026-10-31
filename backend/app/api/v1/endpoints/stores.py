import time
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.app.core.database import get_db
from backend.app.models.stores import Store
from backend.app.models.users import User
from backend.app.schemas.stores import StoreResponse, StoreUpdate
from backend.app.services.auth_service import get_current_user, require_owner_role

router = APIRouter(prefix="/stores", tags=["Stores"])


@router.get("/current", response_model=StoreResponse)
async def get_current_store(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    query = select(Store).where(Store.id == current_user.store_id)
    res = await session.execute(query)
    store = res.scalar_one_or_none()
    if not store:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Toko tidak ditemukan.")
    return store


@router.put("/current", response_model=StoreResponse)
async def update_current_store(
    store_update: StoreUpdate,
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    query = select(Store).where(Store.id == current_user.store_id)
    res = await session.execute(query)
    store = res.scalar_one_or_none()
    if not store:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Toko tidak ditemukan.")

    update_data = store_update.model_dump(exclude_unset=True)
    for k, v in update_data.items():
        setattr(store, k, v)

    store.updated_at = int(time.time() * 1000)
    await session.commit()
    await session.refresh(store)
    return store
