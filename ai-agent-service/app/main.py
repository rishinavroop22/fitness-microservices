"""FastAPI entry point for the AI agent service."""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app.agent.graph import KnowledgeAgent
from app.config import Settings, get_settings
from app.ingestion import IngestionConsumer, handle_ingest
from app.knowledge_base import KnowledgeBase
from app.llm import build_chat_model, build_embeddings
from app.mcp_tools import McpToolRegistry

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("ai-agent-service")


# ------------------------------------------------------------------- schemas
class HistoryTurn(BaseModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str


class AgentRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    userId: str = ""
    department: str | None = None
    conversationId: str | None = None
    history: list[HistoryTurn] = []


class AgentResponse(BaseModel):
    answer: str
    intent: str | None
    standaloneQuestion: str | None
    citations: list[dict]
    toolCalls: list[dict]
    gapReported: bool
    steps: list[dict]


class IndexRequest(BaseModel):
    documentId: str
    title: str
    content: str
    department: str = "ALL"
    fileName: str = ""
    tags: list[str] = []


class SearchRequest(BaseModel):
    query: str
    department: str | None = None
    k: int = Field(default=5, ge=1, le=20)


# ---------------------------------------------------------------------- app
async def _start_with_retry(consumer: IngestionConsumer, delay: float = 5.0) -> None:
    """RabbitMQ may come up after this service (e.g. in docker compose); keep trying in the background."""
    while True:
        try:
            await consumer.start()
            return
        except Exception as exc:
            log.warning("RabbitMQ not reachable (%s); retrying in %.0fs", exc, delay)
            await asyncio.sleep(delay)


def create_app(settings: Settings | None = None, start_consumer: bool | None = None) -> FastAPI:
    settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        kb = KnowledgeBase(settings, build_embeddings(settings))
        registry = McpToolRegistry.from_settings(settings)
        app.state.kb = kb
        app.state.mcp = registry
        app.state.agent = KnowledgeAgent(
            llm=build_chat_model(settings),
            knowledge_base=kb,
            tool_provider=registry.get_tools,
            max_rewrites=settings.max_query_rewrites,
            top_k=settings.retrieval_top_k,
        )
        consumer = IngestionConsumer(settings, kb)
        connect_task = None
        if start_consumer if start_consumer is not None else settings.rabbitmq_enabled:
            connect_task = asyncio.create_task(_start_with_retry(consumer))
        yield
        if connect_task:
            connect_task.cancel()
        await consumer.stop()

    app = FastAPI(title="Enterprise Knowledge AI Agent", version="1.0.0", lifespan=lifespan)

    @app.get("/health")
    async def health():
        return {
            "status": "UP",
            "llmProvider": settings.llm_provider,
            "chunks": app.state.kb.count(),
            "mcpError": app.state.mcp.last_error,
        }

    @app.post("/agent/invoke", response_model=AgentResponse)
    async def invoke(req: AgentRequest):
        state = await app.state.agent.run(
            question=req.question,
            user_id=req.userId,
            department=req.department,
            history=[t.model_dump() for t in req.history],
            conversation_id=req.conversationId,
        )
        return AgentResponse(
            answer=state.get("answer", ""),
            intent=state.get("intent"),
            standaloneQuestion=state.get("standalone_question"),
            citations=state.get("citations", []),
            toolCalls=state.get("tool_calls", []),
            gapReported=state.get("gap_reported", False),
            steps=state.get("steps", []),
        )

    @app.post("/knowledge/search")
    async def search(req: SearchRequest):
        return [c.to_dict() for c in app.state.kb.search(req.query, req.k, req.department)]

    @app.post("/knowledge/index")
    async def index(req: IndexRequest):
        """Synchronous indexing for local development; production uses the RabbitMQ pipeline."""
        status = handle_ingest(app.state.kb, req.model_dump())
        if status["status"] == "FAILED":
            raise HTTPException(status_code=422, detail=status["error"])
        return status

    @app.delete("/knowledge/{document_id}")
    async def delete(document_id: str):
        app.state.kb.delete_document(document_id)
        return {"documentId": document_id, "deleted": True}

    @app.get("/mcp/tools")
    async def mcp_tools():
        tools = await app.state.mcp.get_tools(force=True)
        return {"tools": [{"name": t.name, "description": t.description} for t in tools],
                "error": app.state.mcp.last_error}

    return app


app = create_app()
