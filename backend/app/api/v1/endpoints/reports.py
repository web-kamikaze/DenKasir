import time
from datetime import datetime, timedelta, timezone
from typing import List
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, func
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.models.products import Product
from backend.app.models.transactions import Transaction
from backend.app.models.cash_flows import CashFlow
from backend.app.models.debts import Debt
from backend.app.models.digital_configs import DigitalConfig
from backend.app.schemas.reports import DashboardSummaryResponse, CashFlowChartResponse, CashFlowChartPoint
from backend.app.services.auth_service import get_current_user

router = APIRouter(prefix="/reports", tags=["Reports & Analytics"])


@router.get("/dashboard", response_model=DashboardSummaryResponse)
async def get_dashboard(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    store_id = current_user.store_id
    
    # Hitung waktu awal hari ini (00:00:00)
    now_dt = datetime.now()
    start_of_day = datetime(now_dt.year, now_dt.month, now_dt.day)
    start_of_day_ts = int(start_of_day.timestamp() * 1000)

    # 1. Penjualan & Transaksi Hari Ini
    trx_q = select(Transaction).where(
        Transaction.store_id == store_id,
        Transaction.created_at >= start_of_day_ts,
        Transaction.deleted_at.is_(None)
    )
    trx_res = await session.execute(trx_q)
    today_trxs = list(trx_res.scalars().all())
    today_sales = sum(t.total_amount for t in today_trxs)
    today_count = len(today_trxs)

    # 2. Laba Bersih Hari Ini (Dari Cash Flows)
    cf_q = select(CashFlow).where(
        CashFlow.store_id == store_id,
        CashFlow.created_at >= start_of_day_ts,
        CashFlow.deleted_at.is_(None)
    )
    cf_res = await session.execute(cf_q)
    today_cfs = list(cf_res.scalars().all())
    today_in = sum(cf.amount for cf in today_cfs if cf.flow_type == "IN")
    today_out = sum(cf.amount for cf in today_cfs if cf.flow_type == "OUT")
    today_profit = today_in - today_out

    # 3. Total Produk & Stok Menipis
    prod_q = select(Product).where(Product.store_id == store_id, Product.deleted_at.is_(None))
    prod_res = await session.execute(prod_q)
    all_prods = list(prod_res.scalars().all())
    total_prods = len(all_prods)
    low_stock_prods = len([p for p in all_prods if p.product_type == "RETAIL" and p.stock <= p.min_stock_alert])

    # 4. Total Piutang (Kasbon) & Hutang Supplier
    debt_q = select(Debt).where(Debt.store_id == store_id, Debt.status != "PAID", Debt.deleted_at.is_(None))
    debt_res = await session.execute(debt_q)
    all_debts = list(debt_res.scalars().all())
    total_piutang = sum((d.total_debt - d.paid_amount) for d in all_debts if d.debt_type == "PIUTANG")
    total_hutang = sum((d.total_debt - d.paid_amount) for d in all_debts if d.debt_type == "HUTANG")

    # 5. Kas di Tangan (Saldo Berjalan Semua Arus Kas)
    all_cf_q = select(CashFlow).where(CashFlow.store_id == store_id, CashFlow.deleted_at.is_(None))
    all_cf_res = await session.execute(all_cf_q)
    all_cfs = list(all_cf_res.scalars().all())
    total_cash_in = sum(c.amount for c in all_cfs if c.flow_type == "IN")
    total_cash_out = sum(c.amount for c in all_cfs if c.flow_type == "OUT")
    cash_in_hand = max(0.0, total_cash_in - total_cash_out)

    # 6. Saldo Pulsa H2H
    h2h_q = select(DigitalConfig).where(DigitalConfig.store_id == store_id)
    h2h_res = await session.execute(h2h_q)
    h2h_cfg = h2h_res.scalar_one_or_none()
    digital_balance = h2h_cfg.balance if h2h_cfg else 0.0

    return DashboardSummaryResponse(
        store_id=store_id,
        today_sales=today_sales,
        today_profit=today_profit,
        today_transactions_count=today_count,
        total_products_count=total_prods,
        low_stock_count=low_stock_prods,
        total_piutang=total_piutang,
        total_hutang=total_hutang,
        cash_in_hand=cash_in_hand,
        digital_balance=digital_balance
    )


@router.get("/chart", response_model=CashFlowChartResponse)
async def get_chart_data(
    days: int = 7,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    store_id = current_user.store_id
    now = datetime.now()
    points: List[CashFlowChartPoint] = []

    for i in range(days - 1, -1, -1):
        target_date = now - timedelta(days=i)
        start_day = datetime(target_date.year, target_date.month, target_date.day)
        end_day = start_day + timedelta(days=1)
        start_ts = int(start_day.timestamp() * 1000)
        end_ts = int(end_day.timestamp() * 1000)

        cf_q = select(CashFlow).where(
            CashFlow.store_id == store_id,
            CashFlow.created_at >= start_ts,
            CashFlow.created_at < end_ts,
            CashFlow.deleted_at.is_(None)
        )
        cf_res = await session.execute(cf_q)
        day_cfs = list(cf_res.scalars().all())

        income = sum(c.amount for c in day_cfs if c.flow_type == "IN")
        expense = sum(c.amount for c in day_cfs if c.flow_type == "OUT")

        points.append(
            CashFlowChartPoint(
                date=start_day.strftime("%d/%m"),
                income=income,
                expense=expense,
                net=income - expense
            )
        )

    return CashFlowChartResponse(
        store_id=store_id,
        period=f"{days} Hari Terakhir",
        points=points
    )
