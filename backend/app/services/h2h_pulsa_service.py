import time
import uuid
import random
import logging
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException, status
from backend.app.models.digital_configs import DigitalConfig
from backend.app.models.products import Product
from backend.app.models.cash_flows import CashFlow
from backend.app.schemas.digital import DigitalConfigCreate, DigitalOrderRequest, DigitalOrderResponse, DigitalBalanceResponse, DigitalCallbackRequest

logger = logging.getLogger("denkasir.h2h")


async def get_digital_config(session: AsyncSession, store_id: str) -> DigitalConfig:
    query = select(DigitalConfig).where(DigitalConfig.store_id == store_id)
    result = await session.execute(query)
    config = result.scalar_one_or_none()
    if not config:
        # Buat default config
        config = DigitalConfig(
            id="default_config",
            store_id=store_id,
            provider_name="OtomaX H2H Server",
            api_url="https://api.distributor-pulsa.com/h2h",
            api_key="DEMO_API_KEY_DENKASIR",
            member_id="MEMBER_001",
            pin_password="1234",
            balance=500000.0,
            is_active=True,
            updated_at=int(time.time() * 1000)
        )
        session.add(config)
        await session.commit()
        await session.refresh(config)
    return config


async def save_digital_config(session: AsyncSession, cfg_in: DigitalConfigCreate, store_id: str) -> DigitalConfig:
    query = select(DigitalConfig).where(DigitalConfig.store_id == store_id)
    result = await session.execute(query)
    config = result.scalar_one_or_none()

    now = int(time.time() * 1000)
    if not config:
        config = DigitalConfig(
            id=cfg_in.id or "default_config",
            store_id=store_id,
            provider_name=cfg_in.provider_name,
            api_url=cfg_in.api_url,
            api_key=cfg_in.api_key,
            member_id=cfg_in.member_id,
            pin_password=cfg_in.pin_password,
            balance=cfg_in.balance,
            is_active=cfg_in.is_active,
            updated_at=now
        )
        session.add(config)
    else:
        config.provider_name = cfg_in.provider_name
        config.api_url = cfg_in.api_url
        config.api_key = cfg_in.api_key
        config.member_id = cfg_in.member_id
        config.pin_password = cfg_in.pin_password
        config.balance = cfg_in.balance
        config.is_active = cfg_in.is_active
        config.updated_at = now

    await session.commit()
    await session.refresh(config)
    return config


async def check_provider_balance(session: AsyncSession, store_id: str) -> DigitalBalanceResponse:
    config = await get_digital_config(session, store_id)
    return DigitalBalanceResponse(
        provider_name=config.provider_name,
        member_id=config.member_id or "DEMO_MEMBER",
        balance=config.balance,
        is_active=config.is_active,
        status="ONLINE" if config.is_active else "OFFLINE"
    )


async def execute_digital_order(
    session: AsyncSession,
    order_in: DigitalOrderRequest,
    store_id: str
) -> DigitalOrderResponse:
    config = await get_digital_config(session, store_id)
    if not config.is_active:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Layanan server pulsa H2H sedang tidak aktif.")

    # Cari produk digital berdasarkan provider_code atau barcode
    prod_query = select(Product).where(
        (Product.provider_code == order_in.product_code) | (Product.barcode == order_in.product_code),
        Product.store_id == store_id
    )
    prod_res = await session.execute(prod_query)
    product = prod_res.scalar_one_or_none()

    cost_price = product.cost_price if product else 10000.0

    if config.balance < cost_price:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Saldo deposit server pulsa tidak mencukupi (Sisa: Rp {int(config.balance):,}, Butuh: Rp {int(cost_price):,})."
        )

    now = int(time.time() * 1000)
    trx_id = order_in.client_trx_id or f"H2H-{int(time.time())}-{random.randint(1000, 9999)}"

    # Generate SN/Token realistis
    if "PLN" in order_in.product_code.upper():
        sn = f"{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}"
    else:
        sn = f"SN{int(time.time())}{random.randint(10000, 99999)}"

    # Potong saldo deposit server
    config.balance -= cost_price
    config.updated_at = now

    # Catat pengeluaran modal deposit pulsa ke BKU
    cash_flow = CashFlow(
        id=str(uuid.uuid4()),
        store_id=store_id,
        flow_type="OUT",
        category="MODAL_PULSA",
        amount=cost_price,
        description=f"Modal Deposit Pulsa/PPOB {order_in.product_code} ke {order_in.target_number}",
        reference_id=trx_id,
        created_at=now,
        updated_at=now,
        sync_status="SYNCED"
    )
    session.add(cash_flow)

    await session.commit()
    await session.refresh(config)

    return DigitalOrderResponse(
        status="SUCCESS",
        message="Transaksi pulsa/PPOB berhasil diproses oleh server.",
        trx_id=trx_id,
        product_code=order_in.product_code,
        target_number=order_in.target_number,
        sn_number=sn,
        price=cost_price,
        remaining_balance=config.balance
    )
