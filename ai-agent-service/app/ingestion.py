"""RabbitMQ consumer for the asynchronous document-ingestion pipeline.

document-service (Spring Boot) ──document.ingest──► [document.ingest.queue] ──► this consumer
                                                                                 │ chunk + embed
                                                                                 ▼
document-service ◄──[document.status.queue]◄──document.status── {INDEXED | FAILED}

Delete events (``document.delete``) remove a document's vectors from ChromaDB.
"""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Any

import aio_pika
from aio_pika.abc import AbstractIncomingMessage, AbstractRobustConnection

from app.config import Settings
from app.knowledge_base import KnowledgeBase

log = logging.getLogger(__name__)


def handle_ingest(kb: KnowledgeBase, event: dict[str, Any]) -> dict[str, Any]:
    """Index one DocumentIngestionEvent; returns the status event to publish back."""
    document_id = str(event["documentId"])
    try:
        text = event.get("content") or ""
        if not text.strip():
            raise ValueError("Document has no extractable text")
        chunks = kb.index_document(
            document_id=document_id,
            title=event.get("title") or document_id,
            text=text,
            department=event.get("department") or "ALL",
            source=event.get("fileName") or "",
            tags=event.get("tags") or [],
        )
        return {"documentId": document_id, "status": "INDEXED", "chunkCount": chunks, "error": None}
    except Exception as exc:
        log.exception("Indexing failed for document %s", document_id)
        return {"documentId": document_id, "status": "FAILED", "chunkCount": 0, "error": str(exc)[:500]}


class IngestionConsumer:
    def __init__(self, settings: Settings, kb: KnowledgeBase):
        self.settings = settings
        self.kb = kb
        self.connection: AbstractRobustConnection | None = None
        self.exchange: aio_pika.abc.AbstractExchange | None = None

    async def start(self) -> None:
        s = self.settings
        self.connection = await aio_pika.connect_robust(s.rabbitmq_url)
        channel = await self.connection.channel()
        await channel.set_qos(prefetch_count=4)
        # Declarations are idempotent and match the Spring Boot RabbitMqConfig.
        self.exchange = await channel.declare_exchange(s.rabbitmq_exchange, aio_pika.ExchangeType.TOPIC, durable=True)
        await channel.declare_queue(f"{s.ingest_queue}.dlq", durable=True)
        ingest = await channel.declare_queue(
            s.ingest_queue, durable=True,
            arguments={"x-dead-letter-exchange": "", "x-dead-letter-routing-key": f"{s.ingest_queue}.dlq"},
        )
        delete = await channel.declare_queue(s.delete_queue, durable=True)
        await ingest.consume(self._on_ingest)
        await delete.consume(self._on_delete)
        log.info("Listening on %s and %s", s.ingest_queue, s.delete_queue)

    async def stop(self) -> None:
        if self.connection:
            await self.connection.close()

    async def _publish_status(self, status: dict[str, Any]) -> None:
        assert self.exchange is not None
        await self.exchange.publish(
            aio_pika.Message(body=json.dumps(status).encode(), content_type="application/json",
                             delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
            routing_key=self.settings.status_routing_key,
        )

    async def _on_ingest(self, message: AbstractIncomingMessage) -> None:
        try:
            event = json.loads(message.body)
        except json.JSONDecodeError:
            log.error("Malformed ingest message, sending to DLQ")
            await message.reject(requeue=False)
            return
        # Embedding calls are blocking; keep the event loop responsive.
        status = await asyncio.to_thread(handle_ingest, self.kb, event)
        await self._publish_status(status)
        await message.ack()

    async def _on_delete(self, message: AbstractIncomingMessage) -> None:
        async with message.process(requeue=False):
            event = json.loads(message.body)
            await asyncio.to_thread(self.kb.delete_document, str(event["documentId"]))
            log.info("Deleted vectors for document %s", event["documentId"])
