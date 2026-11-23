import uuid

import pytest
from langchain_core.tools import StructuredTool

from app.agent.graph import KnowledgeAgent
from app.config import Settings
from app.fake_llm import HashingEmbeddings, RuleBasedFakeChatModel
from app.knowledge_base import KnowledgeBase

LEAVE_POLICY = """Annual Leave Policy

Full-time employees accrue 24 days of paid annual leave per calendar year. Leave requests must be
submitted in the HR portal at least 10 working days in advance and approved by the reporting manager.

Up to 5 unused leave days may be carried forward into the next year. Carried-forward days expire on 31 March.
"""

VPN_GUIDE = """VPN Access Guide

Engineering staff connect to internal systems through the corporate VPN. Install the GlobalConnect client,
sign in with single sign-on and approve the multi-factor prompt. VPN sessions disconnect after 12 hours.
"""


@pytest.fixture
def settings():
    return Settings(llm_provider="fake", chroma_mode="memory", chroma_collection=f"test_{uuid.uuid4().hex}",
                    chunk_size=400, chunk_overlap=50, rabbitmq_enabled=False, mcp_enabled=False)


@pytest.fixture
def kb(settings):
    kb = KnowledgeBase(settings, HashingEmbeddings())
    kb.index_document("doc-leave", "Annual Leave Policy", LEAVE_POLICY, department="ALL", source="leave.pdf")
    kb.index_document("doc-vpn", "VPN Access Guide", VPN_GUIDE, department="ENGINEERING", source="vpn.md")
    return kb


class ToolRecorder:
    def __init__(self):
        self.calls = []

    def tools(self):
        def search_document_catalog(query: str, department: str = "") -> str:
            """Search the enterprise document catalog by title or tag."""
            self.calls.append(("search_document_catalog", {"query": query}))
            return '[{"id": "doc-leave", "title": "Annual Leave Policy", "status": "INDEXED", "uploadedBy": "hr-admin"}]'

        def get_user_profile(userId: str) -> str:
            """Get the profile of a user."""
            self.calls.append(("get_user_profile", {"userId": userId}))
            return f'{{"id": "{userId}", "department": "ENGINEERING"}}'

        def report_knowledge_gap(question: str, userId: str, reason: str) -> str:
            """Record a question the knowledge base could not answer."""
            self.calls.append(("report_knowledge_gap", {"question": question, "userId": userId}))
            return '{"id": "gap-1", "status": "OPEN"}'

        return [StructuredTool.from_function(f) for f in
                (search_document_catalog, get_user_profile, report_knowledge_gap)]


@pytest.fixture
def recorder():
    return ToolRecorder()


@pytest.fixture
def agent(kb, recorder):
    async def provider():
        return recorder.tools()

    return KnowledgeAgent(RuleBasedFakeChatModel(), kb, provider, max_rewrites=2, top_k=4)
