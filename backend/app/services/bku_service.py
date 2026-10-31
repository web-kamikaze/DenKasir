import time
import uuid
from typing import List, Optional, Dict, Any
from datetime import datetime, timezone
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, func
from fastapi import HTTPException, status
from backend.app.models.cash_flows import CashFlow, BkuAccount
from backend.app.schemas.cash_flows import CashFlowCreate, CashFlowUpdate, CashFlowSummaryResponse, BkuAccountCreate


async def get_cash_flows(
    session: AsyncSession,
    store_id: str,
    flow_type: Optional[str] = None,
    category: Optional[str] = None,
    start_time: Optional[int] = None,
    end_time: Optional[int] = None,
    limit: int = 100,
    offset: int = 0
) -> List[CashFlow]:
    filters = [CashFlow.store_id == store_id, CashFlow.deleted_at.is_(None)]
    if flow_type:
        filters.append(CashFlow.flow_type == flow_type)
    if category and category != "Semua":
        filters.append(CashFlow.category == category)
    if start_time:
        filters.append(CashFlow.created_at >= start_time)
    if end_time:
        filters.append(CashFlow.created_at <= end_time)

    query = (
        select(CashFlow)
        .where(and_(*filters))
        .order_by(CashFlow.created_at.desc())
        .limit(limit)
        .offset(offset)
    )
    result = await session.execute(query)
    return list(result.scalars().all())


async def create_cash_flow(session: AsyncSession, cf_in: CashFlowCreate, store_id: str) -> CashFlow:
    now = int(time.time() * 1000)
    cf_id = cf_in.id or str(uuid.uuid4())
    cash_flow = CashFlow(
        id=cf_id,
        store_id=store_id,
        flow_type=cf_in.flow_type,
        category=cf_in.category,
        amount=cf_in.amount,
        description=cf_in.description,
        reference_id=cf_in.reference_id,
        created_at=now,
        updated_at=now,
        sync_status="SYNCED"
    )
    session.add(cash_flow)
    await session.commit()
    await session.refresh(cash_flow)
    return cash_flow


async def get_cash_flow_summary(session: AsyncSession, store_id: str, start_time: Optional[int] = None, end_time: Optional[int] = None) -> CashFlowSummaryResponse:
    filters = [CashFlow.store_id == store_id, CashFlow.deleted_at.is_(None)]
    if start_time:
        filters.append(CashFlow.created_at >= start_time)
    if end_time:
        filters.append(CashFlow.created_at <= end_time)

    query = select(CashFlow).where(and_(*filters))
    result = await session.execute(query)
    items = list(result.scalars().all())

    total_in = sum(item.amount for item in items if item.flow_type == "IN")
    total_out = sum(item.amount for item in items if item.flow_type == "OUT")
    net_profit = total_in - total_out

    return CashFlowSummaryResponse(
        total_in=total_in,
        total_out=total_out,
        net_profit=net_profit,
        period="Semua Periode" if not start_time else "Kustom",
        transaction_count=len(items)
    )


# BKU Accounts Management
async def get_bku_accounts(session: AsyncSession, store_id: str) -> List[BkuAccount]:
    query = select(BkuAccount).where(BkuAccount.store_id == store_id).order_by(BkuAccount.created_at.asc())
    result = await session.execute(query)
    accounts = list(result.scalars().all())
    
    # Auto-seed default accounts jika belum ada
    if not accounts:
        defaults = [
            ("Kas Toko (Laci Kasir)", "IN", "101", True),
            ("Penjualan Barang Retail", "IN", "401", True),
            ("Penjualan Pulsa & PPOB", "IN", "402", True),
            ("Penerimaan Piutang / Kasbon", "IN", "102", True),
            ("Belanja Stok Barang Toko", "OUT", "501", True),
            ("Deposit Pulsa H2H / Saldo", "OUT", "502", True),
            ("Biaya Operasional & Listrik", "OUT", "601", True),
            ("Gaji Karyawan", "OUT", "602", True),
            ("Prive / Ambil Kas Pemilik", "OUT", "301", True),
        ]
        now = int(time.time() * 1000)
        for name, acc_type, code, is_def in defaults:
            acc = BkuAccount(
                id=str(uuid.uuid4()),
                store_id=store_id,
                account_name=name,
                account_type=acc_type,
                account_code=code,
                is_default=is_def,
                created_at=now,
                updated_at=now
            )
            session.add(acc)
            accounts.append(acc)
        await session.commit()

    return accounts


async def create_bku_account(session: AsyncSession, acc_in: BkuAccountCreate, store_id: str) -> BkuAccount:
    now = int(time.time() * 1000)
    acc = BkuAccount(
        id=acc_in.id or str(uuid.uuid4()),
        store_id=store_id,
        account_name=acc_in.account_name,
        account_type=acc_in.account_type,
        account_code=acc_in.account_code,
        is_default=acc_in.is_default,
        created_at=now,
        updated_at=now
    )
    session.add(acc)
    await session.commit()
    await session.refresh(acc)
    return acc
