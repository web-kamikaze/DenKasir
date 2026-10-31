import pytest
import time
import uuid
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_offline_first_sync_push_and_pull(client: AsyncClient, owner_token: str):
    now = int(time.time() * 1000)
    offline_prod_id = str(uuid.uuid4())
    offline_trx_id = str(uuid.uuid4())

    push_payload = {
        "store_id": "default_store",
        "client_timestamp": now,
        "products": [
            {
                "id": offline_prod_id,
                "barcode": "999888777666",
                "name": "Sabun Mandi Lifebuoy 100g",
                "category": "Pcs",
                "product_type": "RETAIL",
                "cost_price": 3500.0,
                "selling_price": 4500.0,
                "stock": 20.0,
                "min_stock_alert": 5.0
            }
        ],
        "transactions": [
            {
                "id": offline_trx_id,
                "invoice_number": "INV-OFFLINE-001",
                "total_amount": 9000.0,
                "cash_given": 10000.0,
                "change_amount": 1000.0,
                "payment_method": "CASH",
                "customer_name": "Warga RT 02",
                "status": "COMPLETED",
                "items": [
                    {
                        "product_id": offline_prod_id,
                        "product_name": "Sabun Mandi Lifebuoy 100g",
                        "quantity": 2.0,
                        "cost_price": 3500.0,
                        "selling_price": 4500.0,
                        "subtotal": 9000.0
                    }
                ]
            }
        ],
        "cash_flows": [],
        "debts": [],
        "stock_opnames": [],
        "purchase_orders": []
    }

    # 1. Push Offline Changes
    push_res = await client.post(
        "/api/v1/sync/push",
        json=push_payload,
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert push_res.status_code == 200
    push_data = push_res.json()
    assert push_data["status"] == "SUCCESS"
    assert push_data["synced_counts"]["products"] == 1
    assert push_data["synced_counts"]["transactions"] == 1

    # 2. Pull Changes (Delta Sync)
    pull_payload = {
        "store_id": "default_store",
        "last_synced_at": now - 10000
    }
    pull_res = await client.post(
        "/api/v1/sync/pull",
        json=pull_payload,
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert pull_res.status_code == 200
    pull_data = pull_res.json()
    assert pull_data["status"] == "SUCCESS"
    assert any(p["id"] == offline_prod_id for p in pull_data["products"])
    assert any(t["id"] == offline_trx_id for t in pull_data["transactions"])
