from typing import Optional, List
from pydantic import BaseModel, ConfigDict


class TransactionItemBase(BaseModel):
    product_id: str
    product_name: str
    quantity: float
    cost_price: float
    selling_price: float
    subtotal: float
    target_phone_number: Optional[str] = None
    sn_number: Optional[str] = None
    digital_status: str = "SUCCESS"


class TransactionItemCreate(TransactionItemBase):
    id: Optional[str] = None


class TransactionItemResponse(TransactionItemBase):
    id: str
    transaction_id: str

    model_config = ConfigDict(from_attributes=True)


class TransactionBase(BaseModel):
    invoice_number: str
    total_amount: float
    cash_given: float = 0.0
    change_amount: float = 0.0
    payment_method: str = "CASH"  # CASH, QRIS, KASBON, TRANSFER
    customer_name: Optional[str] = None
    status: str = "COMPLETED"


class TransactionCreate(TransactionBase):
    id: Optional[str] = None
    store_id: Optional[str] = "default_store"
    user_id: Optional[str] = "user_1"
    items: List[TransactionItemCreate]


class TransactionResponse(TransactionBase):
    id: str
    store_id: str
    user_id: str
    created_at: int
    updated_at: int
    deleted_at: Optional[int] = None
    sync_status: str
    items: List[TransactionItemResponse] = []

    model_config = ConfigDict(from_attributes=True)
