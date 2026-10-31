import time
import uuid
from typing import List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from fastapi import HTTPException, status
from backend.app.models.debts import Debt
from backend.app.models.cash_flows import CashFlow
from backend.app.schemas.debts import DebtCreate, DebtUpdate, DebtPaymentRequest


async def get_debts(
    session: AsyncSession,
    store_id: str,
    debt_type: Optional[str] = None,
    status_filter: Optional[str] = None,
    search: Optional[str] = None
) -> List[Debt]:
    filters = [Debt.store_id == store_id, Debt.deleted_at.is_(None)]
    if debt_type:
        filters.append(Debt.debt_type == debt_type)
    if status_filter and status_filter != "Semua":
        filters.append(Debt.status == status_filter)
    if search:
        search_fmt = f"%{search}%"
        filters.append(Debt.customer_name.ilike(search_fmt))

    query = select(Debt).where(and_(*filters)).order_by(Debt.created_at.desc())
    result = await session.execute(query)
    return list(result.scalars().all())


async def get_debt_by_id(session: AsyncSession, debt_id: str, store_id: str) -> Optional[Debt]:
    query = select(Debt).where(Debt.id == debt_id, Debt.store_id == store_id, Debt.deleted_at.is_(None))
    result = await session.execute(query)
    return result.scalar_one_or_none()


async def create_debt(session: AsyncSession, debt_in: DebtCreate, store_id: str) -> Debt:
    now = int(time.time() * 1000)
    debt = Debt(
        id=debt_in.id or str(uuid.uuid4()),
        store_id=store_id,
        customer_name=debt_in.customer_name,
        customer_phone=debt_in.customer_phone,
        total_debt=debt_in.total_debt,
        paid_amount=debt_in.paid_amount,
        status=debt_in.status,
        debt_type=debt_in.debt_type,
        due_date=debt_in.due_date or (now + (7 * 24 * 3600 * 1000)),
        created_at=now,
        updated_at=now,
        sync_status="SYNCED"
    )
    session.add(debt)
    await session.commit()
    await session.refresh(debt)
    return debt


async def pay_debt(
    session: AsyncSession,
    debt_id: str,
    store_id: str,
    payment_data: DebtPaymentRequest
) -> Debt:
    debt = await get_debt_by_id(session, debt_id, store_id)
    if not debt:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Catatan hutang/piutang tidak ditemukan.")

    now = int(time.time() * 1000)
    new_paid = debt.paid_amount + payment_data.payment_amount
    debt.paid_amount = min(debt.total_debt, new_paid)

    if debt.paid_amount >= debt.total_debt:
        debt.status = "PAID"
    elif debt.paid_amount > 0:
        debt.status = "PARTIAL"
    else:
        debt.status = "UNPAID"

    debt.updated_at = now
    debt.sync_status = "SYNCED"

    # Otomatis catat mutasi ke BKU
    if debt.debt_type == "PIUTANG":
        # Pelanggan bayar kasbon -> Kas Masuk (IN)
        cash_flow = CashFlow(
            id=str(uuid.uuid4()),
            store_id=store_id,
            flow_type="IN",
            category="PIUTANG",
            amount=payment_data.payment_amount,
            description=f"Penerimaan Cicilan Kasbon: {debt.customer_name}" + (f" ({payment_data.note})" if payment_data.note else ""),
            reference_id=debt.id,
            created_at=now,
            updated_at=now,
            sync_status="SYNCED"
        )
        session.add(cash_flow)
    else:
        # Bayar hutang supplier -> Kas Keluar (OUT)
        cash_flow = CashFlow(
            id=str(uuid.uuid4()),
            store_id=store_id,
            flow_type="OUT",
            category="HUTANG",
            amount=payment_data.payment_amount,
            description=f"Pembayaran Hutang Supplier: {debt.customer_name}" + (f" ({payment_data.note})" if payment_data.note else ""),
            reference_id=debt.id,
            created_at=now,
            updated_at=now,
            sync_status="SYNCED"
        )
        session.add(cash_flow)

    await session.commit()
    await session.refresh(debt)
    return debt
