import time
import uuid
from typing import List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from fastapi import HTTPException, status
from backend.app.models.stock_opnames import StockOpname
from backend.app.models.products import Product
from backend.app.models.purchase_orders import PurchaseOrder
from backend.app.schemas.stock_opnames import StockOpnameCreate
from backend.app.schemas.purchase_orders import PurchaseOrderCreate, PurchaseOrderUpdate


async def get_stock_opnames(session: AsyncSession, store_id: str) -> List[StockOpname]:
    query = select(StockOpname).where(StockOpname.store_id == store_id).order_by(StockOpname.created_at.desc())
    result = await session.execute(query)
    return list(result.scalars().all())


async def create_stock_opname(session: AsyncSession, opname_in: StockOpnameCreate, store_id: str) -> StockOpname:
    now = int(time.time() * 1000)
    opname = StockOpname(
        id=opname_in.id or str(uuid.uuid4()),
        store_id=store_id,
        product_id=opname_in.product_id,
        system_stock=opname_in.system_stock,
        physical_stock=opname_in.physical_stock,
        difference=opname_in.difference,
        reason=opname_in.reason,
        created_at=now,
        sync_status="SYNCED"
    )
    session.add(opname)

    # Sesuaikan stok riil produk di database
    prod_query = select(Product).where(Product.id == opname_in.product_id, Product.store_id == store_id)
    prod_res = await session.execute(prod_query)
    product = prod_res.scalar_one_or_none()
    if product:
        product.stock = opname_in.physical_stock
        product.updated_at = now

    await session.commit()
    await session.refresh(opname)
    return opname


# Purchase Orders
async def get_purchase_orders(session: AsyncSession, store_id: str) -> List[PurchaseOrder]:
    query = select(PurchaseOrder).where(PurchaseOrder.store_id == store_id).order_by(PurchaseOrder.created_at.desc())
    result = await session.execute(query)
    return list(result.scalars().all())


async def create_purchase_order(session: AsyncSession, po_in: PurchaseOrderCreate, store_id: str) -> PurchaseOrder:
    now = int(time.time() * 1000)
    po = PurchaseOrder(
        id=po_in.id or str(uuid.uuid4()),
        store_id=store_id,
        supplier_name=po_in.supplier_name,
        date_text=po_in.date_text,
        item_count=po_in.item_count,
        total_amount=po_in.total_amount,
        status=po_in.status,
        created_at=now,
        updated_at=now
    )
    session.add(po)
    await session.commit()
    await session.refresh(po)
    return po


async def update_purchase_order(session: AsyncSession, po_id: str, store_id: str, po_in: PurchaseOrderUpdate) -> PurchaseOrder:
    query = select(PurchaseOrder).where(PurchaseOrder.id == po_id, PurchaseOrder.store_id == store_id)
    result = await session.execute(query)
    po = result.scalar_one_or_none()
    if not po:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Purchase Order tidak ditemukan.")

    update_data = po_in.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(po, field, value)

    po.updated_at = int(time.time() * 1000)
    await session.commit()
    await session.refresh(po)
    return po
