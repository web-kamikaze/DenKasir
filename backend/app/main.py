import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from backend.app.core.config import settings
from backend.app.core.database import init_db
from backend.app.core.init_data import init_seed_data
from backend.app.api.v1.api import api_router

# Logging configuration
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("denkasir")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup Lifecycle
    logger.info("Starting DenKasir Backend API Server...")
    await init_db()
    await init_seed_data()
    logger.info("DenKasir Backend API Server is ready.")
    yield
    # Shutdown Lifecycle
    logger.info("Shutting down DenKasir Backend API Server...")


app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="REST API Server & Cloud Sync Hub untuk Aplikasi Kasir DenKasir (Offline-First Android POS & Pembukuan Kelontong + PPOB)",
    lifespan=lifespan,
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    docs_url="/docs",
    redoc_url="/redoc"
)

# CORS Setup
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API v1 Router
app.include_router(api_router, prefix=settings.API_V1_STR)


@app.get("/", tags=["Health Check"])
async def root():
    return {
        "status": "ONLINE",
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "docs": "/docs",
        "api_v1": settings.API_V1_STR
    }


@app.get("/health", tags=["Health Check"])
async def health_check():
    return {
        "status": "HEALTHY",
        "database": "CONNECTED",
        "service": "DenKasir FastAPI"
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.app.main:app", host="0.0.0.0", port=8000, reload=True)
