from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.schemas.transactions import TransactionResponse, TransactionCreate
from backend.app.services.auth_service import get_current_user
from backend.app.services.transaction_service import get_transactions, get_transaction_by_id, create_transaction
from backend.app.services.excel_service import export_transactions_excel

router = APIRouter(prefix="/transactions", tags=["Transactions"])


@router.get("", response_model=List[TransactionResponse])
async def list_transactions(
    limit: int = 100,
    offset: int = 0,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await get_transactions(session, current_user.store_id, limit, offset)


@router.get("/{trx_id}", response_model=TransactionResponse)
async def get_single_transaction(
    trx_id: str,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    trx = await get_transaction_by_id(session, trx_id, current_user.store_id)
    if not trx:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Transaksi tidak ditemukan.")
    return trx


@router.post("", response_model=TransactionResponse)
async def create_new_transaction(
    trx_in: TransactionCreate,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await create_transaction(session, trx_in, current_user.store_id, current_user.id)


@router.get("/export/excel")
async def export_excel(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    excel_stream = await export_transactions_excel(session, current_user.store_id)
    headers = {"Content-Disposition": 'attachment; filename="laporan_penjualan_denkasir.xlsx"'}
    return StreamingResponse(
        excel_stream,
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers=headers
    )
