from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.schemas.digital import (
    DigitalConfigResponse,
    DigitalConfigCreate,
    DigitalOrderRequest,
    DigitalOrderResponse,
    DigitalBalanceResponse,
    DigitalCallbackRequest,
)
from backend.app.services.auth_service import get_current_user, require_owner_role
from backend.app.services.h2h_pulsa_service import (
    get_digital_config,
    save_digital_config,
    check_provider_balance,
    execute_digital_order,
)

router = APIRouter(prefix="/pulsa", tags=["Pulsa & PPOB H2H Gateway"])


@router.get("/config", response_model=DigitalConfigResponse)
async def get_config(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await get_digital_config(session, current_user.store_id)


@router.post("/config", response_model=DigitalConfigResponse)
async def update_config(
    cfg_in: DigitalConfigCreate,
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    return await save_digital_config(session, cfg_in, current_user.store_id)


@router.get("/balance", response_model=DigitalBalanceResponse)
async def check_balance(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await check_provider_balance(session, current_user.store_id)


@router.post("/order", response_model=DigitalOrderResponse)
async def place_order(
    order_in: DigitalOrderRequest,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await execute_digital_order(session, order_in, current_user.store_id)


@router.post("/callback")
async def webhook_callback(
    callback_in: DigitalCallbackRequest,
    session: AsyncSession = Depends(get_db)
):
    """Webhook callback dari distributor pulsa eksternal."""
    return {"status": "ACK", "trx_id": callback_in.trx_id, "message": "Callback processed successfully."}
