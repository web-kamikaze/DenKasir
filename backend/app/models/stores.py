import uuid
import time
from sqlalchemy import Column, String, BigInteger, Boolean, Index
from backend.app.core.database import Base


class Store(Base):
    __tablename__ = "stores"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    name = Column(String(200), nullable=False)
    address = Column(String(500), nullable=True)
    phone = Column(String(50), nullable=True)
    owner_id = Column(String(36), nullable=False, index=True)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    updated_at = Column(BigInteger, default=lambda: int(time.time() * 1000), onupdate=lambda: int(time.time() * 1000), nullable=False)
    sync_status = Column(String(20), default="SYNCED", nullable=False)

    __table_args__ = (
        Index("idx_stores_owner", "owner_id"),
        Index("idx_stores_updated_at", "updated_at"),
    )
