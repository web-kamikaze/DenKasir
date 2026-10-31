import time
import uuid
import logging
from typing import Dict, Any, List
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, or_
from backend.app.models.products import Product
from backend.app.models.transactions import Transaction, TransactionItem
from backend.app.models.cash_flows import CashFlow, BkuAccount
from backend.app.models.debts import Debt
from backend.app.models.stock_opnames import StockOpname
from backend.app.models.purchase_orders import PurchaseOrder
from backend.app.models.sync_logs import SyncLog
from backend.app.schemas.sync import (
    SyncPushPayload,
    SyncPushResponse,
    SyncPullPayload,
    SyncPullResponse,
)
from backend.app.schemas.products import ProductResponse
from backend.app.schemas.transactions import TransactionResponse, TransactionItemResponse
from backend.app.schemas.cash_flows import CashFlowResponse, BkuAccountResponse
from backend.app.schemas.debts import DebtResponse
from backend.app.schemas.stock_opnames import StockOpnameResponse
from backend.app.schemas.purchase_orders import PurchaseOrderResponse

logger = logging.getLogger("denkasir.sync")


async def process_sync_push(
    session: AsyncSession,
    payload: SyncPushPayload,
    user_id: str
) -> SyncPushResponse:
    now = int(time.time() * 1000)
    store_id = payload.store_id
    synced_counts = {
        "products": 0,
        "transactions": 0,
        "cash_flows": 0,
        "debts": 0,
        "stock_opnames": 0,
        "purchase_orders": 0
    }
    errors: List[str] = []

    # 1. Sync Products (Upsert with Idempotency)
    for p_in in payload.products:
        try:
            prod_id = p_in.id or str(uuid.uuid4())
            existing = await session.get(Product, prod_id)
            if existing:
                # Update existing jika client timestamp lebih baru atau sama
                existing.barcode = p_in.barcode
                existing.name = p_in.name
                existing.category = p_in.category
                existing.product_type = p_in.product_type
                existing.cost_price = p_in.cost_price
                existing.selling_price = p_in.selling_price
                existing.stock = p_in.stock
                existing.min_stock_alert = p_in.min_stock_alert
                existing.image_path = p_in.image_path
                existing.provider_code = p_in.provider_code
                existing.updated_at = now
                existing.sync_status = "SYNCED"
            else:
                new_prod = Product(
                    id=prod_id,
                    store_id=store_id,
                    barcode=p_in.barcode,
                    name=p_in.name,
                    category=p_in.category,
                    product_type=p_in.product_type,
                    cost_price=p_in.cost_price,
                    selling_price=p_in.selling_price,
                    stock=p_in.stock,
                    min_stock_alert=p_in.min_stock_alert,
                    image_path=p_in.image_path,
                    provider_code=p_in.provider_code,
                    created_at=now,
                    updated_at=now,
                    sync_status="SYNCED"
                )
                session.add(new_prod)
            synced_counts["products"] += 1
        except Exception as e:
            errors.append(f"Product {p_in.name}: {str(e)}")

    # 2. Sync Transactions & Items
    for t_in in payload.transactions:
        try:
            trx_id = t_in.id or str(uuid.uuid4())
            existing_t = await session.get(Transaction, trx_id)
            if not existing_t:
                new_trx = Transaction(
                    id=trx_id,
                    store_id=store_id,
                    user_id=t_in.user_id or user_id,
                    invoice_number=t_in.invoice_number,
                    total_amount=t_in.total_amount,
                    cash_given=t_in.cash_given,
                    change_amount=t_in.change_amount,
                    payment_method=t_in.payment_method,
                    customer_name=t_in.customer_name,
                    status=t_in.status,
                    created_at=now,
                    updated_at=now,
                    sync_status="SYNCED"
                )
                session.add(new_trx)

                for it in t_in.items:
                    it_id = it.id or str(uuid.uuid4())
                    new_item = TransactionItem(
                        id=it_id,
                        transaction_id=trx_id,
                        product_id=it.product_id,
                        product_name=it.product_name,
                        quantity=it.quantity,
                        cost_price=it.cost_price,
                        selling_price=it.selling_price,
                        subtotal=it.subtotal,
                        target_phone_number=it.target_phone_number,
                        sn_number=it.sn_number,
                        digital_status=it.digital_status
                    )
                    session.add(new_item)
            synced_counts["transactions"] += 1
        except Exception as e:
            errors.append(f"Transaction {t_in.invoice_number}: {str(e)}")

    # 3. Sync Cash Flows
    for cf_in in payload.cash_flows:
        try:
            cf_id = cf_in.id or str(uuid.uuid4())
            existing_cf = await session.get(CashFlow, cf_id)
            if not existing_cf:
                new_cf = CashFlow(
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
                session.add(new_cf)
            synced_counts["cash_flows"] += 1
        except Exception as e:
            errors.append(f"CashFlow {cf_in.description}: {str(e)}")

    # 4. Sync Debts
    for d_in in payload.debts:
        try:
            d_id = d_in.id or str(uuid.uuid4())
            existing_d = await session.get(Debt, d_id)
            if existing_d:
                existing_d.paid_amount = d_in.paid_amount
                existing_d.status = d_in.status
                existing_d.updated_at = now
            else:
                new_d = Debt(
                    id=d_id,
                    store_id=store_id,
                    customer_name=d_in.customer_name,
                    customer_phone=d_in.customer_phone,
                    total_debt=d_in.total_debt,
                    paid_amount=d_in.paid_amount,
                    status=d_in.status,
                    debt_type=d_in.debt_type,
                    due_date=d_in.due_date,
                    created_at=now,
                    updated_at=now,
                    sync_status="SYNCED"
                )
                session.add(new_d)
            synced_counts["debts"] += 1
        except Exception as e:
            errors.append(f"Debt {d_in.customer_name}: {str(e)}")

    # 5. Sync Stock Opnames
    for so_in in payload.stock_opnames:
        try:
            so_id = so_in.id or str(uuid.uuid4())
            existing_so = await session.get(StockOpname, so_id)
            if not existing_so:
                new_so = StockOpname(
                    id=so_id,
                    store_id=store_id,
                    product_id=so_in.product_id,
                    system_stock=so_in.system_stock,
                    physical_stock=so_in.physical_stock,
                    difference=so_in.difference,
                    reason=so_in.reason,
                    created_at=now,
                    sync_status="SYNCED"
                )
                session.add(new_so)
            synced_counts["stock_opnames"] += 1
        except Exception as e:
            errors.append(f"StockOpname: {str(e)}")

    # 6. Sync Purchase Orders
    for po_in in payload.purchase_orders:
        try:
            po_id = po_in.id or str(uuid.uuid4())
            existing_po = await session.get(PurchaseOrder, po_id)
            if existing_po:
                existing_po.status = po_in.status
                existing_po.total_amount = po_in.total_amount
                existing_po.item_count = po_in.item_count
                existing_po.updated_at = now
            else:
                new_po = PurchaseOrder(
                    id=po_id,
                    store_id=store_id,
                    supplier_name=po_in.supplier_name,
                    date_text=po_in.date_text,
                    item_count=po_in.item_count,
                    total_amount=po_in.total_amount,
                    status=po_in.status,
                    created_at=now,
                    updated_at=now
                )
                session.add(new_po)
            synced_counts["purchase_orders"] += 1
        except Exception as e:
            errors.append(f"PO {po_in.supplier_name}: {str(e)}")

    # Record log
    total_records = sum(synced_counts.values())
    sync_log = SyncLog(
        id=str(uuid.uuid4()),
        store_id=store_id,
        user_id=user_id,
        sync_direction="PUSH",
        status="SUCCESS" if not errors else "PARTIAL",
        records_synced=total_records,
        client_timestamp=payload.client_timestamp,
        server_timestamp=now,
        details=f"Synced: {synced_counts}. Errors: {errors}"
    )
    session.add(sync_log)

    await session.commit()

    return SyncPushResponse(
        status="SUCCESS" if not errors else "PARTIAL",
        server_timestamp=now,
        synced_counts=synced_counts,
        errors=errors
    )


async def process_sync_pull(
    session: AsyncSession,
    payload: SyncPullPayload,
    user_id: str
) -> SyncPullResponse:
    now = int(time.time() * 1000)
    store_id = payload.store_id
    last_sync = payload.last_synced_at

    # Ambil delta produk
    p_query = select(Product).where(Product.store_id == store_id, Product.updated_at >= last_sync)
    p_res = await session.execute(p_query)
    products = [ProductResponse.model_validate(p) for p in p_res.scalars().all()]

    # Ambil delta transaksi
    t_query = select(Transaction).where(Transaction.store_id == store_id, Transaction.updated_at >= last_sync)
    t_res = await session.execute(t_query)
    transactions = [TransactionResponse.model_validate(t) for t in t_res.scalars().all()]

    # Ambil delta cash flows
    cf_query = select(CashFlow).where(CashFlow.store_id == store_id, CashFlow.updated_at >= last_sync)
    cf_res = await session.execute(cf_query)
    cash_flows = [CashFlowResponse.model_validate(cf) for cf in cf_res.scalars().all()]

    # Ambil bku accounts
    bku_query = select(BkuAccount).where(BkuAccount.store_id == store_id)
    bku_res = await session.execute(bku_query)
    bku_accounts = [BkuAccountResponse.model_validate(ba) for ba in bku_res.scalars().all()]

    # Ambil debts
    d_query = select(Debt).where(Debt.store_id == store_id, Debt.updated_at >= last_sync)
    d_res = await session.execute(d_query)
    debts = [
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
            sync_status="SYNCED"
        )
        for d in d_res.scalars().all()
    ]

    # Ambil stock opnames
    so_query = select(StockOpname).where(StockOpname.store_id == store_id, StockOpname.created_at >= last_sync)
    so_res = await session.execute(so_query)
    stock_opnames = [StockOpnameResponse.model_validate(so) for so in so_res.scalars().all()]

    # Ambil purchase orders
    po_query = select(PurchaseOrder).where(PurchaseOrder.store_id == store_id, PurchaseOrder.updated_at >= last_sync)
    po_res = await session.execute(po_query)
    purchase_orders = [PurchaseOrderResponse.model_validate(po) for po in po_res.scalars().all()]

    # Catat sync log
    total_pulled = len(products) + len(transactions) + len(cash_flows) + len(debts) + len(stock_opnames) + len(purchase_orders)
    sync_log = SyncLog(
        id=str(uuid.uuid4()),
        store_id=store_id,
        user_id=user_id,
        sync_direction="PULL",
        status="SUCCESS",
        records_synced=total_pulled,
        client_timestamp=last_sync,
        server_timestamp=now,
        details=f"Pulled {total_pulled} records since timestamp {last_sync}"
    )
    session.add(sync_log)
    await session.commit()

    return SyncPullResponse(
        status="SUCCESS",
        server_timestamp=now,
        products=products,
        transactions=transactions,
        cash_flows=cash_flows,
        bku_accounts=bku_accounts,
        debts=debts,
        stock_opnames=stock_opnames,
        purchase_orders=purchase_orders
    )
