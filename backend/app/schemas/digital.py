from typing import Optional, List
from pydantic import BaseModel, ConfigDict


class DigitalConfigBase(BaseModel):
    provider_name: str = "OtomaX H2H"
    api_url: str = "https://api.distributor-pulsa.com/h2h"
    api_key: str = ""
    member_id: str = ""
    pin_password: str = ""
    balance: float = 0.0
    is_active: bool = True


class DigitalConfigCreate(DigitalConfigBase):
    id: Optional[str] = "default_config"
    store_id: Optional[str] = "default_store"


class DigitalConfigResponse(DigitalConfigBase):
    id: str
    store_id: str
    updated_at: int

    model_config = ConfigDict(from_attributes=True)


class DigitalOrderRequest(BaseModel):
    product_code: str  # cth: S10, PLN20
    target_number: str  # cth: 081234567890
    client_trx_id: Optional[str] = None
    store_id: Optional[str] = "default_store"


class DigitalOrderResponse(BaseModel):
    status: str  # SUCCESS, PENDING, FAILED
    message: str
    trx_id: str
    product_code: str
    target_number: str
    sn_number: Optional[str] = None
    price: float
    remaining_balance: float


class DigitalBalanceResponse(BaseModel):
    provider_name: str
    member_id: str
    balance: float
    is_active: bool
    status: str


class DigitalCallbackRequest(BaseModel):
    trx_id: str
    client_trx_id: Optional[str] = None
    product_code: str
    target_number: str
    status: str  # SUCCESS, FAILED
    sn_number: Optional[str] = None
    message: Optional[str] = None
