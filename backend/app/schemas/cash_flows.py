from typing import Optional, List
from pydantic import BaseModel, ConfigDict


class CashFlowBase(BaseModel):
    flow_type: str  # IN or OUT
    category: str
    amount: float
    description: str
    reference_id: Optional[str] = None


class CashFlowCreate(CashFlowBase):
    id: Optional[str] = None
    store_id: Optional[str] = "default_store"


class CashFlowUpdate(BaseModel):
    flow_type: Optional[str] = None
    category: Optional[str] = None
    amount: Optional[float] = None
    description: Optional[str] = None
    reference_id: Optional[str] = None


class CashFlowResponse(CashFlowBase):
    id: str
    store_id: str
    created_at: int
    updated_at: int
    deleted_at: Optional[int] = None
    sync_status: str

    model_config = ConfigDict(from_attributes=True)


class CashFlowSummaryResponse(BaseModel):
    total_in: float
    total_out: float
    net_profit: float
    period: str
    transaction_count: int


class BkuAccountBase(BaseModel):
    account_name: str
    account_type: str  # IN or OUT
    account_code: Optional[str] = None
    is_default: bool = False


class BkuAccountCreate(BkuAccountBase):
    id: Optional[str] = None
    store_id: Optional[str] = "default_store"


class BkuAccountResponse(BkuAccountBase):
    id: str
    store_id: str
    created_at: int
    updated_at: int

    model_config = ConfigDict(from_attributes=True)
