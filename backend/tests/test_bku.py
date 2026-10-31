import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_bku_cash_flow_and_summary(client: AsyncClient, owner_token: str):
    # Buat Pengeluaran Operasional
    cf_in = {
        "flow_type": "OUT",
        "category": "OPERASIONAL",
        "amount": 50000.0,
        "description": "Beli token listrik toko & kresek"
    }
    create_res = await client.post(
        "/api/v1/cash-flows",
        json=cf_in,
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert create_res.status_code == 200
    assert create_res.json()["amount"] == 50000.0

    # Cek Summary
    summary_res = await client.get("/api/v1/cash-flows/summary", headers={"Authorization": f"Bearer {owner_token}"})
    assert summary_res.status_code == 200
    summary_data = summary_res.json()
    assert "total_in" in summary_data
    assert "total_out" in summary_data

    # Cek BKU Accounts
    accounts_res = await client.get("/api/v1/cash-flows/accounts", headers={"Authorization": f"Bearer {owner_token}"})
    assert accounts_res.status_code == 200
    assert len(accounts_res.json()) > 0
