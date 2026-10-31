import time
import uuid
from typing import List, Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, or_, and_, update
from fastapi import HTTPException, status
from backend.app.models.products import Product
from backend.app.schemas.products import ProductCreate, ProductUpdate


async def get_products(
    session: AsyncSession,
    store_id: str,
    category: Optional[str] = None,
    product_type: Optional[str] = None,
    search: Optional[str] = None,
    include_deleted: bool = False
) -> List[Product]:
    filters = [Product.store_id == store_id]
    if not include_deleted:
        filters.append(Product.deleted_at.is_(None))
    if category and category != "Semua" and category != "Scan":
        filters.append(Product.category == category)
    if product_type:
        filters.append(Product.product_type == product_type)
    if search:
        search_fmt = f"%{search}%"
        filters.append(or_(Product.name.ilike(search_fmt), Product.barcode.ilike(search_fmt)))

    query = select(Product).where(and_(*filters)).order_by(Product.name.asc())
    result = await session.execute(query)
    return list(result.scalars().all())


async def get_product_by_id(session: AsyncSession, product_id: str, store_id: str) -> Optional[Product]:
    query = select(Product).where(Product.id == product_id, Product.store_id == store_id, Product.deleted_at.is_(None))
    result = await session.execute(query)
    return result.scalar_one_or_none()


async def get_product_by_barcode(session: AsyncSession, barcode: str, store_id: str) -> Optional[Product]:
    query = select(Product).where(Product.barcode == barcode, Product.store_id == store_id, Product.deleted_at.is_(None))
    result = await session.execute(query)
    return result.scalar_one_or_none()


async def create_product(session: AsyncSession, product_in: ProductCreate, store_id: str) -> Product:
    now = int(time.time() * 1000)
    # Cek duplikasi barcode di store yang sama
    if product_in.barcode:
        existing = await get_product_by_barcode(session, product_in.barcode, store_id)
        if existing:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Barcode '{product_in.barcode}' sudah terdaftar untuk produk '{existing.name}'.")

    prod_id = product_in.id or str(uuid.uuid4())
    product = Product(
        id=prod_id,
        store_id=store_id,
        barcode=product_in.barcode,
        name=product_in.name,
        category=product_in.category,
        product_type=product_in.product_type,
        cost_price=product_in.cost_price,
        selling_price=product_in.selling_price,
        stock=product_in.stock,
        min_stock_alert=product_in.min_stock_alert,
        image_path=product_in.image_path,
        provider_code=product_in.provider_code,
        created_at=now,
        updated_at=now,
        sync_status="SYNCED"
    )
    session.add(product)
    await session.commit()
    await session.refresh(product)
    return product


async def update_product(session: AsyncSession, product_id: str, store_id: str, product_in: ProductUpdate) -> Product:
    product = await get_product_by_id(session, product_id, store_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Produk tidak ditemukan.")

    update_data = product_in.model_dump(exclude_unset=True)
    if "barcode" in update_data and update_data["barcode"] and update_data["barcode"] != product.barcode:
        existing = await get_product_by_barcode(session, update_data["barcode"], store_id)
        if existing and existing.id != product_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Barcode '{update_data['barcode']}' sudah digunakan produk lain.")

    for field, value in update_data.items():
        setattr(product, field, value)

    product.updated_at = int(time.time() * 1000)
    product.sync_status = "SYNCED"
    await session.commit()
    await session.refresh(product)
    return product


async def delete_product(session: AsyncSession, product_id: str, store_id: str) -> bool:
    product = await get_product_by_id(session, product_id, store_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Produk tidak ditemukan.")

    product.deleted_at = int(time.time() * 1000)
    product.updated_at = int(time.time() * 1000)
    product.sync_status = "SYNCED"
    await session.commit()
    return True
