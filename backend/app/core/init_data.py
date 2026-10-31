import logging
import time
import uuid
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.app.core.database import AsyncSessionLocal
from backend.app.core.security import get_password_hash
from backend.app.models.stores import Store
from backend.app.models.users import User
from backend.app.models.products import Product
from backend.app.models.cash_flows import BkuAccount
from backend.app.models.digital_configs import DigitalConfig

logger = logging.getLogger("denkasir.init")


async def init_seed_data():
    """Seed data default awal untuk kemudahan testing lokal & cloud."""
    async with AsyncSessionLocal() as session:
        try:
            now = int(time.time() * 1000)

            # 1. Seed Default Store
            store_q = select(Store).where(Store.id == "default_store")
            store_res = await session.execute(store_q)
            store = store_res.scalar_one_or_none()
            if not store:
                store = Store(
                    id="default_store",
                    name="Toko DenKasir Barokah",
                    address="Jl. Pemuda No. 88, Semarang",
                    phone="081234567890",
                    owner_id="owner_1",
                    is_active=True,
                    created_at=now,
                    updated_at=now,
                    sync_status="SYNCED"
                )
                session.add(store)

            # 2. Seed Default Users
            owner_q = select(User).where(User.username == "owner")
            owner_res = await session.execute(owner_q)
            if not owner_res.scalar_one_or_none():
                owner = User(
                    id="owner_1",
                    store_id="default_store",
                    username="owner",
                    password_hash=get_password_hash("admin123"),
                    full_name="Pemilik Toko (Owner)",
                    role="OWNER",
                    is_active=True,
                    created_at=now
                )
                session.add(owner)

            kasir_q = select(User).where(User.username == "kasir")
            kasir_res = await session.execute(kasir_q)
            if not kasir_res.scalar_one_or_none():
                kasir = User(
                    id="kasir_1",
                    store_id="default_store",
                    username="kasir",
                    password_hash=get_password_hash("kasir123"),
                    full_name="Kasir 1 (Staff)",
                    role="KASIR",
                    is_active=True,
                    created_at=now
                )
                session.add(kasir)

            # 3. Seed Default Products jika masih kosong
            prod_count_q = select(Product).where(Product.store_id == "default_store")
            prod_count_res = await session.execute(prod_count_q)
            if not prod_count_res.scalars().first():
                sample_products = [
                    # RETAIL PCS
                    ("899276111101", "Minyak Goreng Bimoli 2L", "Pcs", "RETAIL", 32000.0, 36000.0, 48.0, 5.0, None),
                    ("899886620022", "Beras Ramos Super 5kg", "Pcs", "RETAIL", 68000.0, 75000.0, 30.0, 5.0, None),
                    ("899999919501", "Gula Pasir Gulaku 1kg", "Pcs", "RETAIL", 15500.0, 18000.0, 40.0, 5.0, None),
                    ("899238811101", "Indomie Goreng Spesial", "Pcs", "RETAIL", 2800.0, 3500.0, 120.0, 10.0, None),
                    ("899100110011", "Kopi Kapal Api Spesial 65g", "Pcs", "RETAIL", 5500.0, 7000.0, 60.0, 5.0, None),
                    ("899138922001", "Teh Botol Sosro Kotak 250ml", "Pcs", "RETAIL", 3200.0, 4500.0, 50.0, 5.0, None),
                    # RETAIL KG
                    (None, "Telur Ayam Negeri Fresh", "Kg", "RETAIL", 26000.0, 29000.0, 25.0, 5.0, None),
                    (None, "Bawang Merah Brebes", "Kg", "RETAIL", 32000.0, 38000.0, 15.0, 3.0, None),
                    (None, "Bawang Putih Kating", "Kg", "RETAIL", 36000.0, 42000.0, 12.0, 3.0, None),
                    (None, "Cabai Rawit Merah", "Kg", "RETAIL", 45000.0, 55000.0, 8.0, 2.0, None),
                    # DIGITAL PULSA / PPOB
                    (None, "Pulsa Telkomsel 10.000", "Pulsa", "DIGITAL", 10200.0, 12000.0, 999.0, 0.0, "TSEL10"),
                    (None, "Pulsa Telkomsel 25.000", "Pulsa", "DIGITAL", 25100.0, 27000.0, 999.0, 0.0, "TSEL25"),
                    (None, "Pulsa Telkomsel 50.000", "Pulsa", "DIGITAL", 49800.0, 52000.0, 999.0, 0.0, "TSEL50"),
                    (None, "Pulsa Indosat 10.000", "Pulsa", "DIGITAL", 10150.0, 12000.0, 999.0, 0.0, "ISAT10"),
                    (None, "Pulsa Indosat 25.000", "Pulsa", "DIGITAL", 24900.0, 27000.0, 999.0, 0.0, "ISAT25"),
                    (None, "Pulsa XL / AXIS 10.000", "Pulsa", "DIGITAL", 10250.0, 12000.0, 999.0, 0.0, "XL10"),
                    (None, "Token PLN 20.000", "Pulsa", "DIGITAL", 20100.0, 22500.0, 999.0, 0.0, "PLN20"),
                    (None, "Token PLN 50.000", "Pulsa", "DIGITAL", 50100.0, 52500.0, 999.0, 0.0, "PLN50"),
                    (None, "Token PLN 100.000", "Pulsa", "DIGITAL", 100100.0, 102500.0, 999.0, 0.0, "PLN100"),
                ]
                for barcode, name, cat, p_type, cost, sell, stk, min_stk, h2h_code in sample_products:
                    p = Product(
                        id=str(uuid.uuid4()),
                        store_id="default_store",
                        barcode=barcode,
                        name=name,
                        category=cat,
                        product_type=p_type,
                        cost_price=cost,
                        selling_price=sell,
                        stock=stk,
                        min_stock_alert=min_stk,
                        provider_code=h2h_code,
                        created_at=now,
                        updated_at=now,
                        sync_status="SYNCED"
                    )
                    session.add(p)

            # 4. Seed BKU Accounts
            bku_q = select(BkuAccount).where(BkuAccount.store_id == "default_store")
            bku_res = await session.execute(bku_q)
            if not bku_res.scalars().first():
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
                for name, acc_type, code, is_def in defaults:
                    acc = BkuAccount(
                        id=str(uuid.uuid4()),
                        store_id="default_store",
                        account_name=name,
                        account_type=acc_type,
                        account_code=code,
                        is_default=is_def,
                        created_at=now,
                        updated_at=now
                    )
                    session.add(acc)

            # 5. Seed Digital Config (OtomaX H2H)
            h2h_cfg_q = select(DigitalConfig).where(DigitalConfig.id == "default_config")
            h2h_cfg_res = await session.execute(h2h_cfg_q)
            if not h2h_cfg_res.scalar_one_or_none():
                h2h_cfg = DigitalConfig(
                    id="default_config",
                    store_id="default_store",
                    provider_name="OtomaX H2H Server",
                    api_url="https://api.distributor-pulsa.com/h2h",
                    api_key="DENKASIR_DEMO_KEY_2026",
                    member_id="MEMBER_001",
                    pin_password="1234",
                    balance=500000.0,
                    is_active=True,
                    updated_at=now
                )
                session.add(h2h_cfg)

            await session.commit()
            logger.info("Database seeded successfully with default accounts and sample catalog.")
        except Exception as e:
            logger.error(f"Error seeding database: {e}")
            await session.rollback()
