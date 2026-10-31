import uuid
import time
from sqlalchemy import Column, String, Float, BigInteger, ForeignKey, Index
from sqlalchemy.orm import relationship
from backend.app.core.database import Base


class Transaction(Base):
    __tablename__ = "transactions"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, default="default_store", index=True)
    user_id = Column(String(36), nullable=False, default="user_1", index=True)
    invoice_number = Column(String(100), nullable=False, index=True)
    total_amount = Column(Float, nullable=False, default=0.0)
    cash_given = Column(Float, nullable=False, default=0.0)
    change_amount = Column(Float, nullable=False, default=0.0)
    payment_method = Column(String(50), default="CASH", nullable=False)  # CASH, QRIS, KASBON, TRANSFER
    customer_name = Column(String(200), nullable=True)
    status = Column(String(50), default="COMPLETED", nullable=False)  # COMPLETED, PENDING, CANCELLED
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    updated_at = Column(BigInteger, default=lambda: int(time.time() * 1000), onupdate=lambda: int(time.time() * 1000), nullable=False)
    deleted_at = Column(BigInteger, nullable=True)
    sync_status = Column(String(20), default="SYNCED", nullable=False)

    items = relationship("TransactionItem", back_populates="transaction", cascade="all, delete-orphan", lazy="selectin")

    __table_args__ = (
        Index("idx_transactions_store_created", "store_id", "created_at"),
        Index("idx_transactions_store_invoice", "store_id", "invoice_number"),
    )


class TransactionItem(Base):
    __tablename__ = "transaction_items"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    transaction_id = Column(String(36), ForeignKey("transactions.id", ondelete="CASCADE"), nullable=False, index=True)
    product_id = Column(String(36), nullable=False, index=True)
    product_name = Column(String(255), nullable=False)
    quantity = Column(Float, default=1.0, nullable=False)
    cost_price = Column(Float, default=0.0, nullable=False)
    selling_price = Column(Float, default=0.0, nullable=False)
    subtotal = Column(Float, default=0.0, nullable=False)
    target_phone_number = Column(String(50), nullable=True)
    sn_number = Column(String(100), nullable=True)
    digital_status = Column(String(50), default="SUCCESS", nullable=False)

    transaction = relationship("Transaction", back_populates="items")
