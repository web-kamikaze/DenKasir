from typing import Optional
from pydantic import BaseModel, ConfigDict


class StoreBase(BaseModel):
    name: str
    address: Optional[str] = None
    phone: Optional[str] = None


class StoreCreate(StoreBase):
    owner_id: Optional[str] = None


class StoreUpdate(BaseModel):
    name: Optional[str] = None
    address: Optional[str] = None
    phone: Optional[str] = None
    is_active: Optional[bool] = None


class StoreResponse(StoreBase):
    id: str
    owner_id: str
    is_active: bool
    created_at: int
    updated_at: int
    sync_status: str

    model_config = ConfigDict(from_attributes=True)
