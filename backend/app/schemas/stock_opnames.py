from typing import Optional
from pydantic import BaseModel, ConfigDict


class StockOpnameBase(BaseModel):
    product_id: str
    system_stock: float
    physical_stock: float
    difference: float
    reason: Optional[str] = None


class StockOpnameCreate(StockOpnameBase):
    id: Optional[str] = None
    store_id: Optional[str] = "default_store"


class StockOpnameResponse(StockOpnameBase):
    id: str
    store_id: str
    created_at: int
    sync_status: str

    model_config = ConfigDict(from_attributes=True)
