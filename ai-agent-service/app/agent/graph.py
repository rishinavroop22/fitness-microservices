"""LangGraph workflow for multi-step reasoning over enterprise knowledge.

    START
      │
  contextualize ── rewrite a follow-up into a standalone question (uses chat history)
      │
  classify_intent ─┬─ smalltalk ─────────────────────────────► respond_smalltalk ─► END
                   ├─ catalog ──► enterprise_tools (MCP) ────► generate_answer ───► END
                   └─ knowledge ► retrieve ► grade_documents ─┬─ relevant ────────► generate_answer
                                     ▲                         ├─ none, retries left ► rewrite_query ─┐
                                     └─────────────────────────┼───────────────────────────────────────┘
                                                               └─ none, no retries ─► report_gap ─► END
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
import time
from typing import Any, Awaitable, Callable

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.tools import BaseTool
from langgraph.graph import END, START, StateGraph

from app.agent import prompts
from app.agent.state import AgentState
from app.knowledge_base import KnowledgeBase
from app.mcp_tools import find_tool

log = logging.getLogger(__name__)

ToolProvider = Callable[[], Awaitable[list[BaseTool]]]

MAX_TOOL_ROUNDS = 4
MAX_HISTORY_TURNS = 6


# ----------------------------------------------------------------------- helpers
def message_text(message: BaseMessage) -> str:
    content = message.content
    if isinstance(content, str):
        return content.strip()
    parts = []
    for part in content:
        if isinstance(part, str):
            parts.append(part)
        elif isinstance(part, dict) and part.get("type") == "text":
            parts.append(part.get("text", ""))
    return "".join(parts).strip()


def parse_json(text: str) -> dict[str, Any] | None:
    """Extract the first JSON object from an LLM reply (tolerates ``` fences and prose)."""
    text = re.sub(r"```(?:json)?", "", text)
    match = re.search(r"\{.*\}", text, re.S)
    if not match:
        return None
    try:
        value = json.loads(match.group(0))
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def format_history(history: list[dict]) -> str:
    turns = history[-MAX_HISTORY_TURNS:]
    return "\n".join(f"{t['role'].capitalize()}: {t['content']}" for t in turns) or "(none)"


def step(node: str, started: float, **detail: Any) -> dict:
    return {"node": node, "ms": round((time.perf_counter() - started) * 1000), **detail}


# ------------------------------------------------------------------------- graph
class KnowledgeAgent:
    def __init__(
        self,
        llm: BaseChatModel,
        knowledge_base: KnowledgeBase,
        tool_provider: ToolProvider,
        max_rewrites: int = 2,
        top_k: int = 6,
    ):
        self.llm = llm
        self.kb = knowledge_base
        self.tool_provider = tool_provider
        self.max_rewrites = max_rewrites
        self.top_k = top_k
        self.graph = self._build()

    async def _ask(self, prompt: str) -> str:
        reply = await self.llm.ainvoke([HumanMessage(content=prompt)])
        return message_text(reply)

    # ---------------------------------------------------------------- nodes
    async def contextualize(self, state: AgentState) -> dict:
        t = time.perf_counter()
        question = state["question"].strip()
        history = state.get("history") or []
        standalone = question
        if history:
            rewritten = await self._ask(
                prompts.CONTEXTUALIZE.format(history=format_history(history), question=question)
            )
            standalone = rewritten or question
        return {
            "standalone_question": standalone,
            "query": standalone,
            "retries": 0,
            "steps": [step("contextualize", t, standaloneQuestion=standalone)],
        }

    async def classify_intent(self, state: AgentState) -> dict:
        t = time.perf_counter()
        reply = await self._ask(prompts.CLASSIFY_INTENT.format(question=state["standalone_question"]))
        parsed = parse_json(reply) or {}
        intent = parsed.get("intent")
        if intent not in ("knowledge", "catalog", "smalltalk"):
            intent = "knowledge"  # safest default: always try the knowledge base
        return {"intent": intent, "steps": [step("classify_intent", t, intent=intent, reason=parsed.get("reason"))]}

    async def retrieve(self, state: AgentState) -> dict:
        t = time.perf_counter()
        chunks = await asyncio.to_thread(self.kb.search, state["query"], self.top_k, state.get("department"))
        retrieved = [c.to_dict() for c in chunks]
        return {
            "retrieved": retrieved,
            "steps": [step("retrieve", t, query=state["query"], hits=len(retrieved))],
        }

    async def grade_documents(self, state: AgentState) -> dict:
        t = time.perf_counter()
        retrieved = state.get("retrieved") or []
        if not retrieved:
            return {"relevant": [], "steps": [step("grade_documents", t, relevant=0, graded=0)]}
        listing = "\n".join(f"[{i}] {c['text']}" for i, c in enumerate(retrieved, 1))
        reply = await self._ask(
            prompts.GRADE_DOCUMENTS.format(question=state["standalone_question"], chunks=listing)
        )
        parsed = parse_json(reply)
        if parsed is not None and isinstance(parsed.get("relevant"), list):
            keep = {int(n) for n in parsed["relevant"] if str(n).isdigit()}
            relevant = [c for i, c in enumerate(retrieved, 1) if i in keep]
        else:
            # grader reply unusable: fall back to the top similarity hits
            relevant = retrieved[:3]
        return {
            "relevant": relevant,
            "steps": [step("grade_documents", t, graded=len(retrieved), relevant=len(relevant))],
        }

    async def rewrite_query(self, state: AgentState) -> dict:
        t = time.perf_counter()
        new_query = await self._ask(
            prompts.REWRITE_QUERY.format(question=state["standalone_question"], query=state["query"])
        )
        retries = state.get("retries", 0) + 1
        return {
            "query": new_query or state["query"],
            "retries": retries,
            "steps": [step("rewrite_query", t, attempt=retries, query=new_query)],
        }

    async def enterprise_tools(self, state: AgentState) -> dict:
        """Tool-calling loop over the MCP tools exposed by the Spring Boot mcp-server."""
        t = time.perf_counter()
        tools = await self.tool_provider()
        if not tools:
            return {
                "tool_calls": [],
                "tool_summary": "Enterprise tools are currently unavailable.",
                "steps": [step("enterprise_tools", t, available=0, calls=0)],
            }
        by_name = {tool.name: tool for tool in tools}
        model = self.llm.bind_tools(tools)
        messages: list[BaseMessage] = [
            SystemMessage(content=prompts.TOOL_AGENT_SYSTEM.format(user_id=state.get("user_id", "unknown"))),
            HumanMessage(content=state["standalone_question"]),
        ]
        calls: list[dict] = []
        reply: AIMessage | None = None
        for _ in range(MAX_TOOL_ROUNDS):
            reply = await model.ainvoke(messages)
            messages.append(reply)
            if not reply.tool_calls:
                break
            for call in reply.tool_calls:
                args = dict(call.get("args") or {})
                tool = by_name.get(call["name"])
                if tool is None:
                    output = f"Unknown tool {call['name']}"
                else:
                    # Never let the model act on behalf of another user or read another department.
                    schema = tool.args or {}
                    if "userId" in schema:
                        args["userId"] = state.get("user_id", "")
                    if "department" in schema:
                        args["department"] = state.get("department") or "ALL"
                    try:
                        output = await tool.ainvoke(args)
                    except Exception as exc:
                        output = f"Tool error: {exc}"
                output_text = output if isinstance(output, str) else json.dumps(output, default=str)
                calls.append({"tool": call["name"], "args": args, "output": output_text[:4000]})
                messages.append(ToolMessage(content=output_text, tool_call_id=call["id"]))
        summary = message_text(reply) if reply is not None and not reply.tool_calls else ""
        return {
            "tool_calls": calls,
            "tool_summary": summary,
            "steps": [step("enterprise_tools", t, available=len(tools), calls=len(calls),
                           tools=[c["tool"] for c in calls])],
        }

    async def generate_answer(self, state: AgentState) -> dict:
        t = time.perf_counter()
        relevant = state.get("relevant") or []
        sources: list[dict] = []
        blocks: list[str] = []
        for chunk in relevant:
            sources.append({"type": "document", **chunk})
            blocks.append(f"[{len(sources)}] (document: {chunk['title']})\n{chunk['text']}")
        for call in state.get("tool_calls") or []:
            sources.append({"type": "tool", "title": f"MCP tool: {call['tool']}", "text": call["output"]})
            blocks.append(f"[{len(sources)}] (enterprise system via {call['tool']})\n{call['output']}")
        if state.get("tool_summary"):
            blocks.append(f"(tool step summary)\n{state['tool_summary']}")

        answer = await self._ask(
            prompts.GENERATE_ANSWER.format(
                question=state["standalone_question"], context="\n\n".join(blocks) or "(no context)"
            )
        )
        cited_numbers = {int(n) for n in re.findall(r"\[(\d+)\]", answer)}
        citations = []
        for number, source in enumerate(sources, 1):
            if number in cited_numbers:
                citations.append({"number": number, **{k: v for k, v in source.items() if k != "text"},
                                  "snippet": source["text"][:300]})
        return {
            "answer": answer,
            "citations": citations,
            "steps": [step("generate_answer", t, sources=len(sources), cited=len(citations))],
        }

    async def report_gap(self, state: AgentState) -> dict:
        t = time.perf_counter()
        tools = await self.tool_provider()
        tool = find_tool(tools, "report_knowledge_gap")
        reported = False
        if tool is not None:
            try:
                await tool.ainvoke({
                    "question": state["standalone_question"],
                    "userId": state.get("user_id", ""),
                    "reason": f"No relevant passages after {state.get('retries', 0)} query rewrites",
                })
                reported = True
            except Exception as exc:
                log.warning("report_knowledge_gap failed: %s", exc)
        dept = state.get("department")
        answer = prompts.NO_ANSWER.format(scope=f" available to the {dept} department" if dept else "")
        if not reported:
            answer = answer.replace(" I've logged it as a knowledge gap so the document owners can add the "
                                    "missing information.", "")
        return {
            "answer": answer,
            "citations": [],
            "gap_reported": reported,
            "steps": [step("report_gap", t, reported=reported)],
        }

    async def respond_smalltalk(self, state: AgentState) -> dict:
        t = time.perf_counter()
        answer = await self._ask(prompts.SMALLTALK.format(question=state["standalone_question"]))
        return {"answer": answer, "citations": [], "steps": [step("respond_smalltalk", t)]}

    # -------------------------------------------------------------- routing
    @staticmethod
    def route_intent(state: AgentState) -> str:
        return {"smalltalk": "respond_smalltalk", "catalog": "enterprise_tools"}.get(state["intent"], "retrieve")

    def route_after_grading(self, state: AgentState) -> str:
        if state.get("relevant"):
            return "generate_answer"
        if state.get("retries", 0) < self.max_rewrites:
            return "rewrite_query"
        return "report_gap"

    def _build(self):
        g = StateGraph(AgentState)
        for name in ("contextualize", "classify_intent", "retrieve", "grade_documents", "rewrite_query",
                     "enterprise_tools", "generate_answer", "report_gap", "respond_smalltalk"):
            g.add_node(name, getattr(self, name))
        g.add_edge(START, "contextualize")
        g.add_edge("contextualize", "classify_intent")
        g.add_conditional_edges("classify_intent", self.route_intent,
                                ["retrieve", "enterprise_tools", "respond_smalltalk"])
        g.add_edge("retrieve", "grade_documents")
        g.add_conditional_edges("grade_documents", self.route_after_grading,
                                ["generate_answer", "rewrite_query", "report_gap"])
        g.add_edge("rewrite_query", "retrieve")
        g.add_edge("enterprise_tools", "generate_answer")
        g.add_edge("generate_answer", END)
        g.add_edge("report_gap", END)
        g.add_edge("respond_smalltalk", END)
        return g.compile()

    # ------------------------------------------------------------------ API
    async def run(self, question: str, user_id: str = "", department: str | None = None,
                  history: list[dict] | None = None, conversation_id: str | None = None) -> AgentState:
        return await self.graph.ainvoke({
            "question": question,
            "user_id": user_id,
            "department": department,
            "history": history or [],
            "conversation_id": conversation_id,
            "steps": [],
        })
