from typing import List
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.schemas.purchase_orders import PurchaseOrderResponse, PurchaseOrderCreate, PurchaseOrderUpdate
from backend.app.services.auth_service import get_current_user
from backend.app.services.stock_service import get_purchase_orders, create_purchase_order, update_purchase_order

router = APIRouter(prefix="/purchase-orders", tags=["Purchase Orders / Restock"])


@router.get("", response_model=List[PurchaseOrderResponse])
async def list_orders(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await get_purchase_orders(session, current_user.store_id)


@router.post("", response_model=PurchaseOrderResponse)
async def create_order(
    po_in: PurchaseOrderCreate,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await create_purchase_order(session, po_in, current_user.store_id)


@router.put("/{po_id}", response_model=PurchaseOrderResponse)
async def update_order(
    po_id: str,
    po_in: PurchaseOrderUpdate,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await update_purchase_order(session, po_id, current_user.store_id, po_in)
