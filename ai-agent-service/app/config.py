"""Runtime configuration, loaded from environment variables (or a .env file)."""

from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # --- LLM / embeddings -------------------------------------------------
    # "gemini" for real use, "fake" for offline development and tests.
    llm_provider: Literal["gemini", "fake"] = "gemini"
    gemini_api_key: str = ""
    gemini_chat_model: str = "gemini-2.5-flash"
    gemini_embedding_model: str = "models/gemini-embedding-001"
    llm_temperature: float = 0.1

    # --- ChromaDB ---------------------------------------------------------
    # chroma_mode = "http" talks to a Chroma server; "local" persists to disk;
    # "memory" is an ephemeral in-process store (tests).
    chroma_mode: Literal["http", "local", "memory"] = "http"
    chroma_host: str = "localhost"
    chroma_port: int = 8001
    chroma_persist_dir: str = "./chroma-data"
    chroma_collection: str = "enterprise_knowledge"

    # --- Chunking & retrieval --------------------------------------------
    chunk_size: int = 1000
    chunk_overlap: int = 150
    retrieval_top_k: int = 6
    max_query_rewrites: int = 2

    # --- RabbitMQ ---------------------------------------------------------
    rabbitmq_enabled: bool = True
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"
    rabbitmq_exchange: str = "knowledge.exchange"
    ingest_queue: str = "document.ingest.queue"
    delete_queue: str = "document.delete.queue"
    status_routing_key: str = "document.status"

    # --- MCP --------------------------------------------------------------
    mcp_enabled: bool = True
    mcp_server_url: str = "http://localhost:8090/sse"
    mcp_transport: Literal["sse", "streamable_http"] = "sse"


@lru_cache
def get_settings() -> Settings:
    return Settings()
