from typing import Optional
from pydantic import BaseModel, ConfigDict


class PurchaseOrderBase(BaseModel):
    supplier_name: str
    date_text: str
    item_count: int = 0
    total_amount: float = 0.0
    status: str = "Draft"  # Draft, Dipesan, Selesai


class PurchaseOrderCreate(PurchaseOrderBase):
    id: Optional[str] = None
    store_id: Optional[str] = "default_store"


class PurchaseOrderUpdate(BaseModel):
    supplier_name: Optional[str] = None
    date_text: Optional[str] = None
    item_count: Optional[int] = None
    total_amount: Optional[float] = None
    status: Optional[str] = None


class PurchaseOrderResponse(PurchaseOrderBase):
    id: str
    store_id: str
    created_at: int
    updated_at: int

    model_config = ConfigDict(from_attributes=True)
