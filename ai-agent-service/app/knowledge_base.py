"""Semantic document store: chunking, embedding and retrieval on ChromaDB via LangChain."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field

import chromadb
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.config import Settings

log = logging.getLogger(__name__)

# Documents uploaded with this department are visible to everyone.
PUBLIC_DEPARTMENT = "ALL"


@dataclass
class RetrievedChunk:
    chunk_id: str
    document_id: str
    title: str
    text: str
    score: float
    chunk_index: int
    department: str
    source: str = ""
    extra: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "chunkId": self.chunk_id,
            "documentId": self.document_id,
            "title": self.title,
            "text": self.text,
            "score": round(self.score, 4),
            "chunkIndex": self.chunk_index,
            "department": self.department,
            "source": self.source,
        }


def build_chroma_client(settings: Settings) -> chromadb.ClientAPI:
    if settings.chroma_mode == "http":
        return chromadb.HttpClient(host=settings.chroma_host, port=settings.chroma_port)
    if settings.chroma_mode == "local":
        return chromadb.PersistentClient(path=settings.chroma_persist_dir)
    return chromadb.EphemeralClient()


class KnowledgeBase:
    def __init__(self, settings: Settings, embeddings: Embeddings, client: chromadb.ClientAPI | None = None):
        self.settings = settings
        self.client = client or build_chroma_client(settings)
        self.store = Chroma(
            collection_name=settings.chroma_collection,
            embedding_function=embeddings,
            client=self.client,
            collection_metadata={"hnsw:space": "cosine"},
        )
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=settings.chunk_size,
            chunk_overlap=settings.chunk_overlap,
            separators=["\n\n", "\n", ". ", " ", ""],
        )

    # ------------------------------------------------------------------ ingest
    def split(self, text: str) -> list[str]:
        return [c.strip() for c in self.splitter.split_text(text) if c.strip()]

    def index_document(
        self,
        document_id: str,
        title: str,
        text: str,
        department: str = PUBLIC_DEPARTMENT,
        source: str = "",
        tags: list[str] | None = None,
    ) -> int:
        """Chunk, embed and upsert a document. Re-indexing replaces old chunks."""
        self.delete_document(document_id)
        chunks = self.split(text)
        if not chunks:
            return 0
        department = (department or PUBLIC_DEPARTMENT).upper()
        docs = [
            Document(
                # Prefix with the title so short chunks still carry their topic.
                page_content=f"{title}\n{chunk}",
                metadata={
                    "documentId": document_id,
                    "title": title,
                    "chunkIndex": i,
                    "department": department,
                    "source": source or title,
                    "tags": ",".join(tags or []),
                },
            )
            for i, chunk in enumerate(chunks)
        ]
        ids = [f"{document_id}:{i}" for i in range(len(docs))]
        self.store.add_documents(docs, ids=ids)
        log.info("Indexed document %s (%s) into %d chunks", document_id, title, len(docs))
        return len(docs)

    def delete_document(self, document_id: str) -> None:
        self.store._collection.delete(where={"documentId": document_id})

    def count(self) -> int:
        return self.store._collection.count()

    # ---------------------------------------------------------------- retrieve
    @staticmethod
    def department_filter(department: str | None) -> dict | None:
        if not department:
            return None
        dept = department.upper()
        if dept == PUBLIC_DEPARTMENT:
            return {"department": PUBLIC_DEPARTMENT}
        return {"$or": [{"department": dept}, {"department": PUBLIC_DEPARTMENT}]}

    def search(self, query: str, k: int | None = None, department: str | None = None) -> list[RetrievedChunk]:
        k = k or self.settings.retrieval_top_k
        if self.count() == 0:
            return []
        results = self.store.similarity_search_with_relevance_scores(
            query, k=k, filter=self.department_filter(department)
        )
        chunks = []
        for doc, score in results:
            md = doc.metadata
            chunks.append(
                RetrievedChunk(
                    chunk_id=f"{md.get('documentId')}:{md.get('chunkIndex')}",
                    document_id=str(md.get("documentId")),
                    title=str(md.get("title", "")),
                    text=doc.page_content,
                    score=float(score),
                    chunk_index=int(md.get("chunkIndex", 0)),
                    department=str(md.get("department", PUBLIC_DEPARTMENT)),
                    source=str(md.get("source", "")),
                )
            )
        return chunks
