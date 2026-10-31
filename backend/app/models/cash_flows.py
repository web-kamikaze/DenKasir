import uuid
import time
from sqlalchemy import Column, String, Float, BigInteger, Boolean, Index
from backend.app.core.database import Base


class CashFlow(Base):
    __tablename__ = "cash_flows"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, default="default_store", index=True)
    flow_type = Column(String(10), nullable=False)  # IN or OUT
    category = Column(String(100), nullable=False)  # PENJUALAN, OPERASIONAL, BELANJA_STOK, MODAL_PULSA, KASBON, PIUTANG, HUTANG, dll.
    amount = Column(Float, nullable=False, default=0.0)
    description = Column(String(500), nullable=False)
    reference_id = Column(String(100), nullable=True, index=True)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    updated_at = Column(BigInteger, default=lambda: int(time.time() * 1000), onupdate=lambda: int(time.time() * 1000), nullable=False)
    deleted_at = Column(BigInteger, nullable=True)
    sync_status = Column(String(20), default="SYNCED", nullable=False)

    __table_args__ = (
        Index("idx_cash_flows_store_flow", "store_id", "flow_type"),
        Index("idx_cash_flows_store_created", "store_id", "created_at"),
    )


class BkuAccount(Base):
    __tablename__ = "bku_accounts"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, default="default_store", index=True)
    account_name = Column(String(200), nullable=False)
    account_type = Column(String(10), nullable=False)  # IN or OUT
    account_code = Column(String(50), nullable=True)
    is_default = Column(Boolean, default=False, nullable=False)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    updated_at = Column(BigInteger, default=lambda: int(time.time() * 1000), onupdate=lambda: int(time.time() * 1000), nullable=False)
