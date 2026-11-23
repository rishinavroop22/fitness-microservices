import sys
from pathlib import Path

from app.agent.graph import KnowledgeAgent
from app.fake_llm import RuleBasedFakeChatModel
from app.mcp_tools import McpToolRegistry

STUB = str(Path(__file__).with_name("mcp_stub_server.py"))


async def test_agent_discovers_and_calls_tools_over_mcp(kb):
    registry = McpToolRegistry({"enterprise": {"command": sys.executable, "args": [STUB], "transport": "stdio"}})
    tools = await registry.get_tools()
    assert {t.name for t in tools} == {"search_document_catalog", "report_knowledge_gap"}

    agent = KnowledgeAgent(RuleBasedFakeChatModel(), kb, registry.get_tools, max_rewrites=0)
    catalog = await agent.run("Which documents do we have about leave?", user_id="u1")
    assert "Annual Leave Policy" in catalog["tool_calls"][0]["output"]

    gap = await agent.run("Quarterly carbon emissions of the Lisbon datacenter", user_id="u1")
    assert gap["gap_reported"] is True


async def test_unreachable_mcp_server_returns_no_tools():
    registry = McpToolRegistry({"enterprise": {"url": "http://127.0.0.1:9/sse", "transport": "sse"}})
    assert await registry.get_tools() == []
    assert registry.last_error
