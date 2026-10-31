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

    all_rows = list(ws.iter_rows(values_only=True))
    if not all_rows:
        return ProductImportResult(total_processed=0, inserted=0, updated=0, failed=0, errors=[])

    # Dynamic header row detection & column mapping
    header_row_idx = -1
    col_map = {
        "barcode": 1,
        "name": 2,
        "category": 3,
        "type": 4,
        "cost_price": 5,
        "selling_price": 6,
        "stock": 7,
        "min_stock": 8,
        "h2h_code": 9,
        "image_path": 10
    }

    for r_idx, row in enumerate(all_rows[:10]):
        if not row:
            continue
        non_empty = [c for c in row if c is not None and str(c).strip() != ""]
        if len(non_empty) < 4:
            continue
        row_str = " ".join(str(c or "").lower() for c in row)
        if "nama" in row_str or ("produk" in row_str and "harga" in row_str):
            header_row_idx = r_idx
            custom_map = {}
            for c_idx, c_val in enumerate(row):
                if not c_val:
                    continue
                c_txt = str(c_val).strip().lower()
                if "barcode" in c_txt or "sku" in c_txt:
                    custom_map["barcode"] = c_idx
                elif "gambar" in c_txt or "foto" in c_txt or "image" in c_txt:
                    custom_map["image_path"] = c_idx
                elif ("nama" in c_txt or "produk" in c_txt or "barang" in c_txt) and "tipe" not in c_txt and "kategori" not in c_txt:
                    custom_map["name"] = c_idx
                elif "kategori" in c_txt or "category" in c_txt:
                    custom_map["category"] = c_idx
                elif "tipe" in c_txt or "type" in c_txt or "jenis" in c_txt:
                    custom_map["type"] = c_idx
                elif "modal" in c_txt or "hpp" in c_txt or "beli" in c_txt:
                    custom_map["cost_price"] = c_idx
                elif "jual" in c_txt or ("harga" in c_txt and "modal" not in c_txt):
                    custom_map["selling_price"] = c_idx
                elif "min" in c_txt and ("stok" in c_txt or "stock" in c_txt):
                    custom_map["min_stock"] = c_idx
                elif ("stok" in c_txt or "stock" in c_txt or "qty" in c_txt) and "min" not in c_txt:
                    custom_map["stock"] = c_idx
                elif "h2h" in c_txt or "provider" in c_txt or "sn" in c_txt:
                    custom_map["h2h_code"] = c_idx

            if "name" in custom_map:
                col_map.update(custom_map)
            break

    start_data_row = header_row_idx + 1 if header_row_idx != -1 else 1

    for row_idx, row in enumerate(all_rows[start_data_row:], start=start_data_row + 1):
        if not row or not any(row):
            continue

        def get_val(key, default=None):
            idx = col_map.get(key)
            if idx is not None and idx < len(row):
                val = row[idx]
                return val if val not in (None, "-", "") else default
            return default

        raw_name = get_val("name")
        if not raw_name:
            continue
        name = str(raw_name).strip()
        if not name:
            continue

        total += 1
        try:
            barcode = str(get_val("barcode", "")).strip() or None
            category = str(get_val("category", "Pcs")).strip() or "Pcs"
            p_type = str(get_val("type", "RETAIL")).strip().upper() or "RETAIL"
            if p_type not in ("RETAIL", "DIGITAL"):
                p_type = "RETAIL"

            cost_raw = get_val("cost_price", 0.0)
            try:
                cost_price = float(cost_raw) if cost_raw is not None else 0.0
            except (ValueError, TypeError):
                cost_price = 0.0

            sell_raw = get_val("selling_price", 0.0)
            try:
                selling_price = float(sell_raw) if sell_raw is not None else 0.0
            except (ValueError, TypeError):
                selling_price = 0.0

            stock_raw = get_val("stock", 0.0)
            try:
                stock = float(stock_raw) if stock_raw is not None else 0.0
            except (ValueError, TypeError):
                stock = 0.0

            min_raw = get_val("min_stock", 5.0)
            try:
                min_stock = float(min_raw) if min_raw is not None else 5.0
            except (ValueError, TypeError):
                min_stock = 5.0

            h2h_code = str(get_val("h2h_code", "")).strip() or None
            img_path = str(get_val("image_path", "")).strip() or None

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
                if img_path:
                    existing.image_path = img_path
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
                    image_path=img_path,
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
