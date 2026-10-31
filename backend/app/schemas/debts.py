from typing import Optional
from pydantic import BaseModel, ConfigDict


class DebtBase(BaseModel):
    customer_name: str
    customer_phone: Optional[str] = None
    total_debt: float
    paid_amount: float = 0.0
    status: str = "UNPAID"  # UNPAID, PARTIAL, PAID
    debt_type: str = "PIUTANG"  # PIUTANG / HUTANG
    due_date: Optional[int] = None


class DebtCreate(DebtBase):
    id: Optional[str] = None
    store_id: Optional[str] = "default_store"


class DebtUpdate(BaseModel):
    customer_name: Optional[str] = None
    customer_phone: Optional[str] = None
    total_debt: Optional[float] = None
    paid_amount: Optional[float] = None
    status: Optional[str] = None
    debt_type: Optional[str] = None
    due_date: Optional[int] = None


class DebtPaymentRequest(BaseModel):
    payment_amount: float
    note: Optional[str] = None


class DebtResponse(DebtBase):
    id: str
    store_id: str
    remaining_debt: float = 0.0
    created_at: int
    updated_at: int
    deleted_at: Optional[int] = None
    sync_status: str

    model_config = ConfigDict(from_attributes=True)
