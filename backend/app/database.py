from motor.motor_asyncio import AsyncIOMotorClient
from pydantic_settings import BaseSettings
from functools import lru_cache
import os
from dotenv import load_dotenv

load_dotenv()


class Settings(BaseSettings):
    mongodb_uri: str = os.getenv("MONGODB_URI", "")
    db_name: str = os.getenv("DB_NAME", "sos")
    host: str = os.getenv("HOST", "0.0.0.0")
    port: int = int(os.getenv("PORT", "8000"))


@lru_cache()
def get_settings() -> Settings:
    return Settings()


_client: AsyncIOMotorClient | None = None


async def get_database():
    global _client
    settings = get_settings()
    if _client is None:
        _client = AsyncIOMotorClient(settings.mongodb_uri)
    return _client[settings.db_name]


async def close_database():
    global _client
    if _client:
        _client.close()
        _client = None
