import uuid
import time
from sqlalchemy import Column, String, Integer, BigInteger, Text, Index
from backend.app.core.database import Base


class SyncLog(Base):
    __tablename__ = "sync_logs"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    store_id = Column(String(36), nullable=False, index=True)
    user_id = Column(String(36), nullable=False)
    sync_direction = Column(String(20), nullable=False)  # PUSH / PULL
    status = Column(String(20), nullable=False)  # SUCCESS / FAILED / PARTIAL
    records_synced = Column(Integer, default=0, nullable=False)
    client_timestamp = Column(BigInteger, nullable=True)
    server_timestamp = Column(BigInteger, default=lambda: int(time.time() * 1000), nullable=False)
    details = Column(Text, nullable=True)

    __table_args__ = (
        Index("idx_sync_logs_store_server_ts", "store_id", "server_timestamp"),
    )
