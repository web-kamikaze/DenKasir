import pytest
import pytest_asyncio
from typing import AsyncGenerator
from httpx import AsyncClient, ASGITransport
from backend.app.main import app
from backend.app.core.database import init_db
from backend.app.core.init_data import init_seed_data


@pytest_asyncio.fixture(scope="function")
async def client() -> AsyncGenerator[AsyncClient, None]:
    await init_db()
    await init_seed_data()
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://testserver"
    ) as ac:
        yield ac


@pytest_asyncio.fixture(scope="function")
async def owner_token(client: AsyncClient) -> str:
    response = await client.post(
        "/api/v1/auth/login",
        data={"username": "owner", "password": "admin123"}
    )
    assert response.status_code == 200, f"{"Login failed: " + response.text}"
    return response.json()["access_token"]


@pytest_asyncio.fixture(scope="function")
async def kasir_token(client: AsyncClient) -> str:
    response = await client.post(
        "/api/v1/auth/login",
        data={"username": "kasir", "password": "kasir123"}
    )
    assert response.status_code == 200, f"{"Login failed: " + response.text}"
    return response.json()["access_token"]