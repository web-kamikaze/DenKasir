import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_login_owner(client: AsyncClient):
    response = await client.post(
        "/api/v1/auth/login",
        data={"username": "owner", "password": "admin123"}
    )
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["role"] == "OWNER"
    assert data["username"] == "owner"


@pytest.mark.asyncio
async def test_login_invalid_password(client: AsyncClient):
    response = await client.post(
        "/api/v1/auth/login",
        data={"username": "owner", "password": "wrongpassword"}
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_get_me(client: AsyncClient, owner_token: str):
    response = await client.get(
        "/api/v1/auth/me",
        headers={"Authorization": f"Bearer {owner_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["username"] == "owner"
    assert data["role"] == "OWNER"
