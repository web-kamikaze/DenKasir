from fastapi import APIRouter
from backend.app.api.v1.endpoints import (
    auth,
    stores,
    users,
    products,
    transactions,
    cash_flows,
    debts,
    stock_opnames,
    purchase_orders,
    h2h_pulsa,
    sync,
    reports,
)

api_router = APIRouter()

api_router.include_router(auth.router)
api_router.include_router(stores.router)
api_router.include_router(users.router)
api_router.include_router(products.router)
api_router.include_router(transactions.router)
api_router.include_router(cash_flows.router)
api_router.include_router(debts.router)
api_router.include_router(stock_opnames.router)
api_router.include_router(purchase_orders.router)
api_router.include_router(h2h_pulsa.router)
api_router.include_router(sync.router)
api_router.include_router(reports.router)
