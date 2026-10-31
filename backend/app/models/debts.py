import uuid
import time
from sqlalchemy import Column, String, Float, BigInteger, Index
from backend.app.core.database import Base


class Debt(Base):
    __tablename__ = "debts"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, default="default_store", index=True)
    customer_name = Column(String(200), nullable=False, index=True)
    customer_phone = Column(String(50), nullable=True)
    total_debt = Column(Float, nullable=False, default=0.0)
    paid_amount = Column(Float, nullable=False, default=0.0)
    status = Column(String(20), default="UNPAID", nullable=False)  # UNPAID, PARTIAL, PAID
    debt_type = Column(String(20), default="PIUTANG", nullable=False)  # PIUTANG (Kasbon) / HUTANG (Supplier)
    due_date = Column(BigInteger, nullable=True)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    updated_at = Column(BigInteger, default=lambda: int(time.time() * 1000), onupdate=lambda: int(time.time() * 1000), nullable=False)
    deleted_at = Column(BigInteger, nullable=True)
    sync_status = Column(String(20), default="SYNCED", nullable=False)

    __table_args__ = (
        Index("idx_debts_store_status", "store_id", "status"),
        Index("idx_debts_store_type", "store_id", "debt_type"),
    )
