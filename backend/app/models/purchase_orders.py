import uuid
import time
from sqlalchemy import Column, String, Float, Integer, BigInteger, Index
from backend.app.core.database import Base


class PurchaseOrder(Base):
    __tablename__ = "purchase_orders"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, default="default_store", index=True)
    supplier_name = Column(String(200), nullable=False)
    date_text = Column(String(50), nullable=False)
    item_count = Column(Integer, default=0, nullable=False)
    total_amount = Column(Float, default=0.0, nullable=False)
    status = Column(String(50), default="Draft", nullable=False)  # Draft, Dipesan, Selesai
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    updated_at = Column(BigInteger, default=lambda: int(time.time() * 1000), onupdate=lambda: int(time.time() * 1000), nullable=False)
