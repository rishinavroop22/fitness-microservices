"""A minimal MCP server used by the integration test (speaks the real MCP protocol over stdio)."""
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("enterprise-stub")


@mcp.tool()
def search_document_catalog(query: str, department: str = "") -> str:
    """Search the enterprise document catalog by title, tag or uploader."""
    return '[{"id": "doc-leave", "title": "Annual Leave Policy", "status": "INDEXED"}]'


@mcp.tool()
def report_knowledge_gap(question: str, userId: str, reason: str) -> str:
    """Record a question the knowledge base could not answer."""
    return '{"id": "gap-42", "status": "OPEN"}'


if __name__ == "__main__":
    mcp.run()
