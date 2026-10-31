import uuid
import time
from sqlalchemy import Column, String, BigInteger, Boolean, Index
from backend.app.core.database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, index=True)
    username = Column(String(100), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    full_name = Column(String(200), nullable=False)
    role = Column(String(20), default="KASIR", nullable=False)  # OWNER / KASIR
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)

    __table_args__ = (
        Index("idx_users_store_role", "store_id", "role"),
    )
