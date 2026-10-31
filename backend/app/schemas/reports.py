from typing import List
from pydantic import BaseModel


class CashFlowChartPoint(BaseModel):
    date: str
    income: float
    expense: float
    net: float


class CashFlowChartResponse(BaseModel):
    store_id: str
    period: str
    points: List[CashFlowChartPoint]


class DashboardSummaryResponse(BaseModel):
    store_id: str
    today_sales: float
    today_profit: float
    today_transactions_count: int
    total_products_count: int
    low_stock_count: int
    total_piutang: float
    total_hutang: float
    cash_in_hand: float
    digital_balance: float
