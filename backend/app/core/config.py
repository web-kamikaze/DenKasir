import os
from typing import List, Optional
from pydantic import AnyHttpUrl
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "backend/.env", "../.env"),
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )

    PROJECT_NAME: str = "DenKasir Server API"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Keamanan & JWT
    SECRET_KEY: str = "denkasir_super_secret_jwt_key_change_in_production_2026"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30 hari untuk kemudahan kasir/mobile

    # Database Configuration (PostgreSQL / SQLite fallback)
    POSTGRES_SERVER: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_USER: str = "postgres"
    POSTGRES_PASSWORD: str = "password"
    POSTGRES_DB: str = "denkasir_db"
    
    # URL Database Async (default: fallback ke SQLite jika PostgreSQL offline saat dev lokal)
    DATABASE_URL: Optional[str] = None
    SYNC_DATABASE_URL: Optional[str] = None
    
    # CORS Origins
    BACKEND_CORS_ORIGINS: List[str] = ["*"]

    # Upload & File Storage
    UPLOAD_DIR: str = "uploads"

    # Default Admin Seed
    FIRST_SUPERUSER_NAME: str = "Owner Toko"
    FIRST_SUPERUSER_USERNAME: str = "owner"
    FIRST_SUPERUSER_PASSWORD: str = "admin123"

    FIRST_CASHIER_NAME: str = "Kasir 1"
    FIRST_CASHIER_USERNAME: str = "kasir"
    FIRST_CASHIER_PASSWORD: str = "kasir123"

    @property
    def get_database_url(self) -> str:
        if self.DATABASE_URL:
            return self.DATABASE_URL
        # Default asyncpg URL jika POSTGRES terpasang
        return f"postgresql+asyncpg://{self.POSTGRES_USER}:{self.POSTGRES_PASSWORD}@{self.POSTGRES_SERVER}:{self.POSTGRES_PORT}/{self.POSTGRES_DB}"

    @property
    def get_sync_database_url(self) -> str:
        if self.SYNC_DATABASE_URL:
            return self.SYNC_DATABASE_URL
        return f"postgresql://{self.POSTGRES_USER}:{self.POSTGRES_PASSWORD}@{self.POSTGRES_SERVER}:{self.POSTGRES_PORT}/{self.POSTGRES_DB}"


settings = Settings()
