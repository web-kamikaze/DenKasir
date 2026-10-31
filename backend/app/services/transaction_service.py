import time
import uuid
from typing import List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from fastapi import HTTPException, status
from backend.app.models.transactions import Transaction, TransactionItem
from backend.app.models.products import Product
from backend.app.models.cash_flows import CashFlow
from backend.app.models.debts import Debt
from backend.app.schemas.transactions import TransactionCreate


async def get_transactions(
    session: AsyncSession,
    store_id: str,
    limit: int = 100,
    offset: int = 0
) -> List[Transaction]:
    query = (
        select(Transaction)
        .where(Transaction.store_id == store_id, Transaction.deleted_at.is_(None))
        .order_by(Transaction.created_at.desc())
        .limit(limit)
        .offset(offset)
    )
    result = await session.execute(query)
    return list(result.scalars().all())


async def get_transaction_by_id(session: AsyncSession, trx_id: str, store_id: str) -> Optional[Transaction]:
    query = select(Transaction).where(
        Transaction.id == trx_id,
        Transaction.store_id == store_id,
        Transaction.deleted_at.is_(None)
    )
    result = await session.execute(query)
    return result.scalar_one_or_none()


async def create_transaction(
    session: AsyncSession,
    trx_in: TransactionCreate,
    store_id: str,
    user_id: str
) -> Transaction:
    now = int(time.time() * 1000)
    trx_id = trx_in.id or str(uuid.uuid4())

    transaction = Transaction(
        id=trx_id,
        store_id=store_id,
        user_id=user_id,
        invoice_number=trx_in.invoice_number,
        total_amount=trx_in.total_amount,
        cash_given=trx_in.cash_given,
        change_amount=trx_in.change_amount,
        payment_method=trx_in.payment_method,
        customer_name=trx_in.customer_name,
        status=trx_in.status,
        created_at=now,
        updated_at=now,
        sync_status="SYNCED"
    )
    session.add(transaction)

    # Tambahkan Transaction Items & Kurangi Stok Retail
    for item_data in trx_in.items:
        item_id = item_data.id or str(uuid.uuid4())
        item = TransactionItem(
            id=item_id,
            transaction_id=trx_id,
            product_id=item_data.product_id,
            product_name=item_data.product_name,
            quantity=item_data.quantity,
            cost_price=item_data.cost_price,
            selling_price=item_data.selling_price,
            subtotal=item_data.subtotal,
            target_phone_number=item_data.target_phone_number,
            sn_number=item_data.sn_number,
            digital_status=item_data.digital_status
        )
        session.add(item)

        # Update stok produk di database jika tipe retail
        prod_query = select(Product).where(Product.id == item_data.product_id, Product.store_id == store_id)
        prod_res = await session.execute(prod_query)
        product = prod_res.scalar_one_or_none()
        if product and product.product_type == "RETAIL":
            product.stock = max(0.0, product.stock - item_data.quantity)
            product.updated_at = now

    # Otomatis catat ke BKU (Cash Flow)
    if trx_in.payment_method in ["CASH", "QRIS", "TRANSFER"]:
        cash_flow = CashFlow(
            id=str(uuid.uuid4()),
            store_id=store_id,
            flow_type="IN",
            category="PENJUALAN",
            amount=trx_in.total_amount,
            description=f"Penjualan POS {trx_in.invoice_number} ({trx_in.payment_method})",
            reference_id=trx_id,
            created_at=now,
            updated_at=now,
            sync_status="SYNCED"
        )
        session.add(cash_flow)
    elif trx_in.payment_method == "KASBON":
        # Jika metode KASBON, buat entri Piutang Pelanggan di tabel Debts
        cust_name = trx_in.customer_name.strip() if trx_in.customer_name else "Pelanggan Umum"
        debt = Debt(
            id=str(uuid.uuid4()),
            store_id=store_id,
            customer_name=cust_name,
            customer_phone=None,
            total_debt=trx_in.total_amount,
            paid_amount=0.0,
            status="UNPAID",
            debt_type="PIUTANG",
            due_date=now + (7 * 24 * 3600 * 1000),  # Default tempo 7 hari
            created_at=now,
            updated_at=now,
            sync_status="SYNCED"
        )
        session.add(debt)

    await session.commit()
    await session.refresh(transaction)
    return transaction
