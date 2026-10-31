import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_create_pos_transaction(client: AsyncClient, kasir_token: str):
    # Ambil produk terlebih dahulu
    prods_res = await client.get("/api/v1/products", headers={"Authorization": f"Bearer {kasir_token}"})
    assert prods_res.status_code == 200
    products = prods_res.json()
    first_prod = products[0]

    trx_payload = {
        "invoice_number": "INV-20260905-001",
        "total_amount": first_prod["selling_price"] * 2,
        "cash_given": 100000.0,
        "change_amount": 100000.0 - (first_prod["selling_price"] * 2),
        "payment_method": "CASH",
        "customer_name": "Pak Budi",
        "status": "COMPLETED",
        "items": [
            {
                "product_id": first_prod["id"],
                "product_name": first_prod["name"],
                "quantity": 2.0,
                "cost_price": first_prod["cost_price"],
                "selling_price": first_prod["selling_price"],
                "subtotal": first_prod["selling_price"] * 2
            }
        ]
    }

    create_trx_res = await client.post(
        "/api/v1/transactions",
        json=trx_payload,
        headers={"Authorization": f"Bearer {kasir_token}"}
    )
    assert create_trx_res.status_code == 200
    trx_data = create_trx_res.json()
    assert trx_data["invoice_number"] == "INV-20260905-001"
    assert len(trx_data["items"]) == 1

    # Verifikasi cash flow otomatis tercatat di BKU
    cf_res = await client.get("/api/v1/cash-flows", headers={"Authorization": f"Bearer {kasir_token}"})
    assert cf_res.status_code == 200
    cfs = cf_res.json()
    assert any("INV-20260905-001" in cf["description"] for cf in cfs)
