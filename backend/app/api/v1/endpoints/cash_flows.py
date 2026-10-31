from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.schemas.cash_flows import (
    CashFlowResponse,
    CashFlowCreate,
    CashFlowSummaryResponse,
    BkuAccountResponse,
    BkuAccountCreate,
)
from backend.app.services.auth_service import get_current_user, require_owner_role
from backend.app.services.bku_service import (
    get_cash_flows,
    create_cash_flow,
    get_cash_flow_summary,
    get_bku_accounts,
    create_bku_account,
)
from backend.app.services.excel_service import export_cash_flow_excel

router = APIRouter(prefix="/cash-flows", tags=["Cash Flows & BKU"])


@router.get("", response_model=List[CashFlowResponse])
async def list_cash_flows(
    flow_type: Optional[str] = None,
    category: Optional[str] = None,
    start_time: Optional[int] = None,
    end_time: Optional[int] = None,
    limit: int = 100,
    offset: int = 0,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await get_cash_flows(session, current_user.store_id, flow_type, category, start_time, end_time, limit, offset)


@router.post("", response_model=CashFlowResponse)
async def create_new_cash_flow(
    cf_in: CashFlowCreate,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await create_cash_flow(session, cf_in, current_user.store_id)


@router.get("/summary", response_model=CashFlowSummaryResponse)
async def summary(
    start_time: Optional[int] = None,
    end_time: Optional[int] = None,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await get_cash_flow_summary(session, current_user.store_id, start_time, end_time)


@router.get("/accounts", response_model=List[BkuAccountResponse])
async def list_accounts(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await get_bku_accounts(session, current_user.store_id)


@router.post("/accounts", response_model=BkuAccountResponse)
async def add_account(
    acc_in: BkuAccountCreate,
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    return await create_bku_account(session, acc_in, current_user.store_id)


@router.get("/export/excel")
async def export_excel(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    excel_stream = await export_cash_flow_excel(session, current_user.store_id)
    headers = {"Content-Disposition": 'attachment; filename="bku_laporan_kas_denkasir.xlsx"'}
    return StreamingResponse(
        excel_stream,
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers=headers
    )
