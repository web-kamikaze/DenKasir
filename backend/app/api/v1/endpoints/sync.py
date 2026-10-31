from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.schemas.sync import (
    SyncPushPayload,
    SyncPushResponse,
    SyncPullPayload,
    SyncPullResponse,
)
from backend.app.services.auth_service import get_current_user
from backend.app.services.sync_service import process_sync_push, process_sync_pull

router = APIRouter(prefix="/sync", tags=["Cloud Sync Adapter"])


@router.post("/push", response_model=SyncPushResponse)
async def sync_push(
    payload: SyncPushPayload,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await process_sync_push(session, payload, current_user.id)


@router.post("/pull", response_model=SyncPullResponse)
async def sync_pull(
    payload: SyncPullPayload,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await process_sync_pull(session, payload, current_user.id)
