from typing import Optional, List
from pydantic import BaseModel, ConfigDict


class ProductBase(BaseModel):
    barcode: Optional[str] = None
    name: str
    category: str = "Pcs"
    product_type: str = "RETAIL"  # RETAIL / DIGITAL
    cost_price: float = 0.0
    selling_price: float = 0.0
    stock: float = 0.0
    min_stock_alert: float = 5.0
    image_path: Optional[str] = None
    provider_code: Optional[str] = None


class ProductCreate(ProductBase):
    id: Optional[str] = None
    store_id: Optional[str] = "default_store"


class ProductUpdate(BaseModel):
    barcode: Optional[str] = None
    name: Optional[str] = None
    category: Optional[str] = None
    product_type: Optional[str] = None
    cost_price: Optional[float] = None
    selling_price: Optional[float] = None
    stock: Optional[float] = None
    min_stock_alert: Optional[float] = None
    image_path: Optional[str] = None
    provider_code: Optional[str] = None


class ProductResponse(ProductBase):
    id: str
    store_id: str
    created_at: int
    updated_at: int
    deleted_at: Optional[int] = None
    sync_status: str

    model_config = ConfigDict(from_attributes=True)


class ProductImportResult(BaseModel):
    total_processed: int
    inserted: int
    updated: int
    failed: int
    errors: List[str] = []
