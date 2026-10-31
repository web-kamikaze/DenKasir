from typing import Optional
from pydantic import BaseModel, ConfigDict


class UserBase(BaseModel):
    username: str
    full_name: str
    role: str = "KASIR"  # OWNER / KASIR


class UserCreate(UserBase):
    password: str
    store_id: Optional[str] = "default_store"


class UserUpdate(BaseModel):
    full_name: Optional[str] = None
    role: Optional[str] = None
    password: Optional[str] = None
    is_active: Optional[bool] = None


class UserResponse(UserBase):
    id: str
    store_id: str
    is_active: bool
    created_at: int

    model_config = ConfigDict(from_attributes=True)
