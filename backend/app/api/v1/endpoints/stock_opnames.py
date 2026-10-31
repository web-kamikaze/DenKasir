from typing import List
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.schemas.stock_opnames import StockOpnameResponse, StockOpnameCreate
from backend.app.services.auth_service import get_current_user
from backend.app.services.stock_service import get_stock_opnames, create_stock_opname

router = APIRouter(prefix="/stock-opnames", tags=["Stock Opname"])


@router.get("", response_model=List[StockOpnameResponse])
async def list_opnames(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await get_stock_opnames(session, current_user.store_id)


@router.post("", response_model=StockOpnameResponse)
async def submit_opname(
    opname_in: StockOpnameCreate,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await create_stock_opname(session, opname_in, current_user.store_id)
