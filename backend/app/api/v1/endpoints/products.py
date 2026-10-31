from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File
from fastapi.responses import StreamingResponse
from sqlalchemy.ext.asyncio import AsyncSession
from backend.app.core.database import get_db
from backend.app.models.users import User
from backend.app.schemas.products import ProductResponse, ProductCreate, ProductUpdate, ProductImportResult
from backend.app.services.auth_service import get_current_user, require_owner_role
from backend.app.services.product_service import (
    get_products,
    get_product_by_id,
    get_product_by_barcode,
    create_product,
    update_product,
    delete_product,
)
from backend.app.services.excel_service import export_products_excel, import_products_excel

router = APIRouter(prefix="/products", tags=["Products"])


@router.get("", response_model=List[ProductResponse])
async def list_products(
    category: Optional[str] = None,
    product_type: Optional[str] = None,
    search: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    return await get_products(
        session=session,
        store_id=current_user.store_id,
        category=category,
        product_type=product_type,
        search=search
    )


@router.get("/barcode/{barcode}", response_model=ProductResponse)
async def find_by_barcode(
    barcode: str,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    product = await get_product_by_barcode(session, barcode, current_user.store_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"Produk dengan barcode '{barcode}' tidak ditemukan.")
    return product


@router.get("/{product_id}", response_model=ProductResponse)
async def get_single_product(
    product_id: str,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    product = await get_product_by_id(session, product_id, current_user.store_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Produk tidak ditemukan.")
    return product


@router.post("", response_model=ProductResponse)
async def create_new_product(
    product_in: ProductCreate,
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    return await create_product(session, product_in, current_user.store_id)


@router.put("/{product_id}", response_model=ProductResponse)
async def update_existing_product(
    product_id: str,
    product_in: ProductUpdate,
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    return await update_product(session, product_id, current_user.store_id, product_in)


@router.delete("/{product_id}")
async def remove_product(
    product_id: str,
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    await delete_product(session, product_id, current_user.store_id)
    return {"message": "Produk berhasil dihapus."}


@router.get("/export/excel")
async def export_excel(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db)
):
    excel_stream = await export_products_excel(session, current_user.store_id)
    headers = {"Content-Disposition": 'attachment; filename="daftar_produk_denkasir.xlsx"'}
    return StreamingResponse(
        excel_stream,
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers=headers
    )


@router.post("/import/excel", response_model=ProductImportResult)
async def import_excel(
    file: UploadFile = File(...),
    current_user: User = Depends(require_owner_role),
    session: AsyncSession = Depends(get_db)
):
    contents = await file.read()
    return await import_products_excel(session, contents, current_user.store_id)
