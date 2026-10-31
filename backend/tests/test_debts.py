import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_debt_flow_and_payment(client: AsyncClient, owner_token: str):
    # Buat Kasbon Pelanggan
    debt_payload = {
        "customer_name": "Ibu Rahma",
        "customer_phone": "081298765432",
        "total_debt": 75000.0,
        "paid_amount": 0.0,
        "status": "UNPAID",
        "debt_type": "PIUTANG"
    }
    create_res = await client.post(
        "/api/v1/debts",
        json=debt_payload,
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert create_res.status_code == 200
    debt_data = create_res.json()
    assert debt_data["customer_name"] == "Ibu Rahma"
    assert debt_data["remaining_debt"] == 75000.0

    # Lakukan Cicilan Pembayaran
    pay_res = await client.post(
        f"/api/v1/debts/{debt_data['id']}/pay",
        json={"payment_amount": 50000.0, "note": "Cicilan ke-1"},
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert pay_res.status_code == 200
    updated_debt = pay_res.json()
    assert updated_debt["paid_amount"] == 50000.0
    assert updated_debt["remaining_debt"] == 25000.0
    assert updated_debt["status"] == "PARTIAL"
