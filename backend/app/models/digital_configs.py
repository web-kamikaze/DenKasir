import uuid
import time
from sqlalchemy import Column, String, Float, BigInteger, Boolean, Index
from backend.app.core.database import Base


class DigitalConfig(Base):
    __tablename__ = "digital_configs"

    id = Column(String(36), primary_key=True, default="default_config")
    store_id = Column(String(36), nullable=False, default="default_store", index=True)
    provider_name = Column(String(100), default="OtomaX H2H", nullable=False)
    api_url = Column(String(500), default="https://api.distributor-pulsa.com/h2h", nullable=False)
    api_key = Column(String(255), default="", nullable=False)
    member_id = Column(String(100), default="", nullable=False)
    pin_password = Column(String(100), default="", nullable=False)
    balance = Column(Float, default=0.0, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    updated_at = Column(BigInteger, default=lambda: int(time.time() * 1000), onupdate=lambda: int(time.time() * 1000), nullable=False)
