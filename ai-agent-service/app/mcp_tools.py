"""Model Context Protocol client.

The agent does not call enterprise back-ends (document catalog, user directory,
knowledge-gap tracker) directly. It discovers them as MCP tools exposed by the
Spring Boot ``mcp-server`` and converts them into LangChain tools, so the same
standard protocol connects the agent to any backend or external tool.
"""

from __future__ import annotations

import asyncio
import logging
import time
from typing import Any

from langchain_core.tools import BaseTool
from langchain_mcp_adapters.client import MultiServerMCPClient

from app.config import Settings

log = logging.getLogger(__name__)


class McpToolRegistry:
    """Loads tools from one or more MCP servers and caches them for a short time."""

    def __init__(self, connections: dict[str, dict[str, Any]], cache_seconds: float = 60.0):
        self.connections = connections
        self.cache_seconds = cache_seconds
        self._tools: list[BaseTool] = []
        self._loaded_at = 0.0
        self._lock = asyncio.Lock()
        self.last_error: str | None = None

    @classmethod
    def from_settings(cls, settings: Settings) -> "McpToolRegistry":
        if not settings.mcp_enabled:
            return cls({})
        return cls({"enterprise": {"url": settings.mcp_server_url, "transport": settings.mcp_transport}})

    async def get_tools(self, force: bool = False) -> list[BaseTool]:
        if not self.connections:
            return []
        async with self._lock:
            fresh = time.monotonic() - self._loaded_at < self.cache_seconds
            if self._tools and fresh and not force:
                return self._tools
            try:
                client = MultiServerMCPClient(self.connections)
                self._tools = await client.get_tools()
                self._loaded_at = time.monotonic()
                self.last_error = None
                log.info("Loaded %d MCP tools: %s", len(self._tools), [t.name for t in self._tools])
            except Exception as exc:  # MCP server down must not break question answering
                self.last_error = f"{type(exc).__name__}: {exc}"
                log.warning("Could not load MCP tools: %s", self.last_error)
            return self._tools


def find_tool(tools: list[BaseTool], name: str) -> BaseTool | None:
    return next((t for t in tools if t.name == name), None)
