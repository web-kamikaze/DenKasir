import logging
from typing import AsyncGenerator
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
from backend.app.core.config import settings

logger = logging.getLogger("denkasir.db")

Base = declarative_base()


def create_db_engine():
    db_url = settings.get_database_url
    if "sqlite" in db_url:
        return create_async_engine(
            db_url,
            echo=False,
            future=True,
            connect_args={"check_same_thread": False}
        )
    else:
        return create_async_engine(
            db_url,
            echo=False,
            future=True,
            pool_size=20,
            max_overflow=10,
            pool_pre_ping=True
        )


engine = create_db_engine()
AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=False
)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """Dependency injection generator untuk FastAPI router."""
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


async def init_db():
    """Inisialisasi tabel database saat startup."""
    global engine, AsyncSessionLocal
    try:
        async with engine.begin() as conn:
            from backend.app.models import (
                stores,
                users,
                products,
                transactions,
                cash_flows,
                debts,
                stock_opnames,
                digital_configs,
                purchase_orders,
                sync_logs
            )
            await conn.run_sync(Base.metadata.create_all)
        logger.info("Database schema initialized successfully.")
    except Exception as e:
        logger.warning(f"Failed to connect to primary database ({e}). Falling back to local SQLite engine...")
        # Fallback ke local SQLite
        fallback_url = "sqlite+aiosqlite:///./denkasir_local.db"
        engine = create_async_engine(fallback_url, echo=False, future=True, connect_args={"check_same_thread": False})
        AsyncSessionLocal = async_sessionmaker(
            bind=engine,
            class_=AsyncSession,
            expire_on_commit=False,
            autoflush=False
        )
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
        logger.info("Local SQLite fallback schema initialized successfully.")
