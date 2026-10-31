import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_h2h_pulsa_config_and_order(client: AsyncClient, owner_token: str):
    # Cek Saldo Provider
    balance_res = await client.get("/api/v1/pulsa/balance", headers={"Authorization": f"Bearer {owner_token}"})
    assert balance_res.status_code == 200
    assert balance_res.json()["status"] == "ONLINE"

    # Eksekusi Tembak Pulsa Telkomsel 10rb
    order_payload = {
        "product_code": "TSEL10",
        "target_number": "081234567890"
    }
    order_res = await client.post(
        "/api/v1/pulsa/order",
        json=order_payload,
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert order_res.status_code == 200
    order_data = order_res.json()
    assert order_data["status"] == "SUCCESS"
    assert order_data["sn_number"] is not None
    assert "SN" in order_data["sn_number"]
