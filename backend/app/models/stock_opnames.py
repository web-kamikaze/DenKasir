import uuid
import time
from sqlalchemy import Column, String, Float, BigInteger, Index
from backend.app.core.database import Base


class StockOpname(Base):
    __tablename__ = "stock_opnames"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, default="default_store", index=True)
    product_id = Column(String(36), nullable=False, index=True)
    system_stock = Column(Float, default=0.0, nullable=False)
    physical_stock = Column(Float, default=0.0, nullable=False)
    difference = Column(Float, default=0.0, nullable=False)
    reason = Column(String(500), nullable=True)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    sync_status = Column(String(20), default="SYNCED", nullable=False)

    __table_args__ = (
        Index("idx_stock_opnames_store_created", "store_id", "created_at"),
    )
