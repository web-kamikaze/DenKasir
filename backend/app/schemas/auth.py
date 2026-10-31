from typing import Optional
from pydantic import BaseModel


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: str
    username: str
    full_name: str
    role: str
    store_id: str
    store_name: Optional[str] = None


class TokenData(BaseModel):
    user_id: Optional[str] = None
    role: Optional[str] = None
    store_id: Optional[str] = None


class LoginRequest(BaseModel):
    username: str
    password: str
