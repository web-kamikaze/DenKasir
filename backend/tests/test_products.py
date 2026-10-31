import pytest
import time
import random
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_list_products(client: AsyncClient, owner_token: str):
    response = await client.get(
        "/api/v1/products",
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) > 0


@pytest.mark.asyncio
async def test_create_and_find_product(client: AsyncClient, owner_token: str):
    unique_barcode = f"899{int(time.time())}{random.randint(100, 999)}"
    payload = {
        "barcode": unique_barcode,
        "name": f"Kecap Manis Bango {random.randint(100, 999)}ml",
        "category": "Pcs",
        "product_type": "RETAIL",
        "cost_price": 21000,
        "selling_price": 25000,
        "stock": 24,
        "min_stock_alert": 5
    }
    create_res = await client.post(
        "/api/v1/products",
        json=payload,
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert create_res.status_code == 200, f"Error creating product: {create_res.text}"
    prod_data = create_res.json()
    assert prod_data["barcode"] == unique_barcode

    # Cari berdasarkan Barcode
    barcode_res = await client.get(
        f"/api/v1/products/barcode/{unique_barcode}",
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert barcode_res.status_code == 200
    assert barcode_res.json()["barcode"] == unique_barcode


@pytest.mark.asyncio
async def test_export_products_excel(client: AsyncClient, owner_token: str):
    res = await client.get(
        "/api/v1/products/export/excel",
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert res.status_code == 200
    assert res.headers["content-type"] == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
