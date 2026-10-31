import uuid
import time
from sqlalchemy import Column, String, Float, BigInteger, Index
from backend.app.core.database import Base


class Product(Base):
    __tablename__ = "products"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, default="default_store", index=True)
    barcode = Column(String(100), nullable=True, index=True)
    name = Column(String(255), nullable=False, index=True)
    category = Column(String(100), default="Pcs", nullable=False)  # Pcs, Kg, Pulsa, dll.
    product_type = Column(String(50), default="RETAIL", nullable=False)  # RETAIL / DIGITAL
    cost_price = Column(Float, default=0.0, nullable=False)
    selling_price = Column(Float, default=0.0, nullable=False)
    stock = Column(Float, default=0.0, nullable=False)
    min_stock_alert = Column(Float, default=5.0, nullable=False)
    image_path = Column(String(500), nullable=True)
    provider_code = Column(String(100), nullable=True)  # Kode H2H Pulsa/PLN (cth: S10, PLN20)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    updated_at = Column(BigInteger, default=lambda: int(time.time() * 1000), onupdate=lambda: int(time.time() * 1000), nullable=False)
    deleted_at = Column(BigInteger, nullable=True)
    sync_status = Column(String(20), default="SYNCED", nullable=False)

    __table_args__ = (
        Index("idx_products_store_category", "store_id", "category"),
        Index("idx_products_store_barcode", "store_id", "barcode"),
        Index("idx_products_updated_at", "updated_at"),
    )
