from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.schemas.debts import DebtResponse, DebtCreate, DebtPaymentRequest
from backend.app.services.auth_service import get_current_user
from backend.app.services.debt_service import get_debts, get_debt_by_id, create_debt, pay_debt

router = APIRouter(prefix="/debts", tags=["Debts (Hutang / Piutang)"])


@router.get("", response_model=List[DebtResponse])
async def list_debts(
    debt_type: Optional[str] = None,
    status_filter: Optional[str] = None,
    search: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    debts = await get_debts(session, current_user.store_id, debt_type, status_filter, search)
    return [
        DebtResponse(
            id=d.id,
            store_id=d.store_id,
            customer_name=d.customer_name,
            customer_phone=d.customer_phone,
            total_debt=d.total_debt,
            paid_amount=d.paid_amount,
            remaining_debt=max(0.0, d.total_debt - d.paid_amount),
            status=d.status,
            debt_type=d.debt_type,
            due_date=d.due_date,
            created_at=d.created_at,
            updated_at=d.updated_at,
            deleted_at=d.deleted_at,
            sync_status=d.sync_status
        )
        for d in debts
    ]


@router.post("", response_model=DebtResponse)
async def create_new_debt(
    debt_in: DebtCreate,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    d = await create_debt(session, debt_in, current_user.store_id)
    return DebtResponse(
        id=d.id,
        store_id=d.store_id,
        customer_name=d.customer_name,
        customer_phone=d.customer_phone,
        total_debt=d.total_debt,
        paid_amount=d.paid_amount,
        remaining_debt=max(0.0, d.total_debt - d.paid_amount),
        status=d.status,
        debt_type=d.debt_type,
        due_date=d.due_date,
        created_at=d.created_at,
        updated_at=d.updated_at,
        deleted_at=d.deleted_at,
        sync_status=d.sync_status
    )


@router.post("/{debt_id}/pay", response_model=DebtResponse)
async def process_payment(
    debt_id: str,
    payment_data: DebtPaymentRequest,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    d = await pay_debt(session, debt_id, current_user.store_id, payment_data)
    return DebtResponse(
        id=d.id,
        store_id=d.store_id,
        customer_name=d.customer_name,
        customer_phone=d.customer_phone,
        total_debt=d.total_debt,
        paid_amount=d.paid_amount,
        remaining_debt=max(0.0, d.total_debt - d.paid_amount),
        status=d.status,
        debt_type=d.debt_type,
        due_date=d.due_date,
        created_at=d.created_at,
        updated_at=d.updated_at,
        deleted_at=d.deleted_at,
        sync_status=d.sync_status
    )
