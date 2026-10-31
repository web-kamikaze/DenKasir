from typing import Optional, List, Dict, Any
from pydantic import BaseModel
from backend.app.schemas.products import ProductCreate, ProductResponse
from backend.app.schemas.transactions import TransactionCreate, TransactionResponse
from backend.app.schemas.cash_flows import CashFlowCreate, CashFlowResponse, BkuAccountCreate, BkuAccountResponse
from backend.app.schemas.debts import DebtCreate, DebtResponse
from backend.app.schemas.stock_opnames import StockOpnameCreate, StockOpnameResponse
from backend.app.schemas.purchase_orders import PurchaseOrderCreate, PurchaseOrderResponse


class SyncPushPayload(BaseModel):
    store_id: str
    client_timestamp: int
    products: List[ProductCreate] = []
    transactions: List[TransactionCreate] = []
    cash_flows: List[CashFlowCreate] = []
    debts: List[DebtCreate] = []
    stock_opnames: List[StockOpnameCreate] = []
    purchase_orders: List[PurchaseOrderCreate] = []


class SyncPushResponse(BaseModel):
    status: str  # SUCCESS, PARTIAL, ERROR
    server_timestamp: int
    synced_counts: Dict[str, int]
    errors: List[str] = []


class SyncPullPayload(BaseModel):
    store_id: str
    last_synced_at: int = 0


class SyncPullResponse(BaseModel):
    status: str
    server_timestamp: int
    products: List[ProductResponse] = []
    transactions: List[TransactionResponse] = []
    cash_flows: List[CashFlowResponse] = []
    bku_accounts: List[BkuAccountResponse] = []
    debts: List[DebtResponse] = []
    stock_opnames: List[StockOpnameResponse] = []
    purchase_orders: List[PurchaseOrderResponse] = []
