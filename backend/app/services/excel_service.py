import io
import time
import uuid
import openpyxl
from openpyxl.styles import Font, Alignment, PatternFill, Border, Side
from typing import List, Tuple
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from backend.app.models.products import Product
from backend.app.models.transactions import Transaction
from backend.app.models.cash_flows import CashFlow
from backend.app.models.debts import Debt
from backend.app.schemas.products import ProductImportResult


def _apply_header_style(ws, headers: List[str]):
    ws.append(headers)
    header_fill = PatternFill(start_color="3E24E2", end_color="3E24E2", fill_type="solid")
    header_font = Font(name="Arial", size=11, bold=True, color="FFFFFF")
    thin_border = Border(
        left=Side(style='thin', color='CBD5E1'),
        right=Side(style='thin', color='CBD5E1'),
        top=Side(style='thin', color='CBD5E1'),
        bottom=Side(style='thin', color='CBD5E1')
    )

    for col_num in range(1, len(headers) + 1):
        cell = ws.cell(row=1, column=col_num)
        cell.fill = header_fill
        cell.font = header_font
        cell.alignment = Alignment(horizontal="center", vertical="center")
        cell.border = thin_border


async def export_products_excel(session: AsyncSession, store_id: str) -> io.BytesIO:
    query = select(Product).where(Product.store_id == store_id, Product.deleted_at.is_(None)).order_by(Product.name.asc())
    result = await session.execute(query)
    products = list(result.scalars().all())

    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "Daftar Produk"

    headers = [
        "No", "Barcode / SKU", "Nama Produk", "Kategori", "Tipe",
        "Harga Modal (HPP)", "Harga Jual", "Stok", "Min Stok Alert", "Kode H2H"
    ]
    _apply_header_style(ws, headers)

    for idx, p in enumerate(products, start=1):
        ws.append([
            idx,
            p.barcode or "-",
            p.name,
            p.category,
            p.product_type,
            p.cost_price,
            p.selling_price,
            p.stock,
            p.min_stock_alert,
            p.provider_code or "-"
        ])

    # Auto-adjust column width
    for col in ws.columns:
        max_len = max(len(str(cell.value or "")) for cell in col)
        col_letter = openpyxl.utils.get_column_letter(col[0].column)
        ws.column_dimensions[col_letter].width = max(max_len + 4, 12)

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)
    return output


async def import_products_excel(session: AsyncSession, file_bytes: bytes, store_id: str) -> ProductImportResult:
    wb = openpyxl.load_workbook(io.BytesIO(file_bytes), data_only=True)
    ws = wb.active

    inserted = 0
    updated = 0
    failed = 0
    errors = []
    total = 0
    now = int(time.time() * 1000)

    # Baris 1 adalah header, mulai baris 2
    for row_idx, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=2):
        if not row or not any(row):
            continue
        total += 1
        try:
            barcode = str(row[1]).strip() if len(row) > 1 and row[1] not in (None, "-", "") else None
            name = str(row[2]).strip() if len(row) > 2 and row[2] else None
            category = str(row[3]).strip() if len(row) > 3 and row[3] else "Pcs"
            p_type = str(row[4]).strip().upper() if len(row) > 4 and row[4] else "RETAIL"
            cost_price = float(row[5]) if len(row) > 5 and row[5] is not None else 0.0
            selling_price = float(row[6]) if len(row) > 6 and row[6] is not None else 0.0
            stock = float(row[7]) if len(row) > 7 and row[7] is not None else 0.0
            min_stock = float(row[8]) if len(row) > 8 and row[8] is not None else 5.0
            h2h_code = str(row[9]).strip() if len(row) > 9 and row[9] not in (None, "-", "") else None

            if not name:
                failed += 1
                errors.append(f"Baris {row_idx}: Nama produk tidak boleh kosong.")
                continue

            # Cek jika barcode sudah ada
            existing = None
            if barcode:
                q = select(Product).where(Product.barcode == barcode, Product.store_id == store_id, Product.deleted_at.is_(None))
                res = await session.execute(q)
                existing = res.scalar_one_or_none()

            if existing:
                existing.name = name
                existing.category = category
                existing.product_type = p_type
                existing.cost_price = cost_price
                existing.selling_price = selling_price
                existing.stock = stock
                existing.min_stock_alert = min_stock
                existing.provider_code = h2h_code
                existing.updated_at = now
                updated += 1
            else:
                new_prod = Product(
                    id=str(uuid.uuid4()),
                    store_id=store_id,
                    barcode=barcode,
                    name=name,
                    category=category,
                    product_type=p_type,
                    cost_price=cost_price,
                    selling_price=selling_price,
                    stock=stock,
                    min_stock_alert=min_stock,
                    provider_code=h2h_code,
                    created_at=now,
                    updated_at=now,
                    sync_status="SYNCED"
                )
                session.add(new_prod)
                inserted += 1

        except Exception as ex:
            failed += 1
            errors.append(f"Baris {row_idx}: {str(ex)}")

    await session.commit()

    return ProductImportResult(
        total_processed=total,
        inserted=inserted,
        updated=updated,
        failed=failed,
        errors=errors[:20]
    )


async def export_transactions_excel(session: AsyncSession, store_id: str) -> io.BytesIO:
    query = select(Transaction).where(Transaction.store_id == store_id, Transaction.deleted_at.is_(None)).order_by(Transaction.created_at.desc())
    result = await session.execute(query)
    transactions = list(result.scalars().all())

    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "Laporan Penjualan POS"

    headers = ["No", "No. Invoice", "Waktu Transaksi", "Metode Bayar", "Pelanggan", "Total Belanja", "Uang Diterima", "Kembalian", "Status"]
    _apply_header_style(ws, headers)

    for idx, t in enumerate(transactions, start=1):
        dt_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(t.created_at / 1000))
        ws.append([
            idx,
            t.invoice_number,
            dt_str,
            t.payment_method,
            t.customer_name or "-",
            t.total_amount,
            t.cash_given,
            t.change_amount,
            t.status
        ])

    for col in ws.columns:
        max_len = max(len(str(cell.value or "")) for cell in col)
        col_letter = openpyxl.utils.get_column_letter(col[0].column)
        ws.column_dimensions[col_letter].width = max(max_len + 4, 14)

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)
    return output


async def export_cash_flow_excel(session: AsyncSession, store_id: str) -> io.BytesIO:
    query = select(CashFlow).where(CashFlow.store_id == store_id, CashFlow.deleted_at.is_(None)).order_by(CashFlow.created_at.asc())
    result = await session.execute(query)
    flows = list(result.scalars().all())

    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "Buku Kas Umum (BKU)"

    headers = ["No", "Tanggal", "Tipe Mutasi", "Kategori", "Deskripsi", "Pemasukan (IN)", "Pengeluaran (OUT)", "Saldo Berjalan"]
    _apply_header_style(ws, headers)

    running_balance = 0.0
    for idx, cf in enumerate(flows, start=1):
        dt_str = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(cf.created_at / 1000))
        income = cf.amount if cf.flow_type == "IN" else 0.0
        expense = cf.amount if cf.flow_type == "OUT" else 0.0
        running_balance += (income - expense)

        ws.append([
            idx,
            dt_str,
            "Pemasukan" if cf.flow_type == "IN" else "Pengeluaran",
            cf.category,
            cf.description,
            income,
            expense,
            running_balance
        ])

    for col in ws.columns:
        max_len = max(len(str(cell.value or "")) for cell in col)
        col_letter = openpyxl.utils.get_column_letter(col[0].column)
        ws.column_dimensions[col_letter].width = max(max_len + 4, 15)

    output = io.BytesIO()
    wb.save(output)
    output.seek(0)
    return output
