from __future__ import annotations

import operator
from typing import Annotated, Literal, TypedDict

Intent = Literal["knowledge", "catalog", "smalltalk"]


class ChatTurn(TypedDict):
    role: Literal["user", "assistant"]
    content: str


class AgentState(TypedDict, total=False):
    # inputs
    question: str
    history: list[ChatTurn]
    user_id: str
    department: str | None
    conversation_id: str | None

    # working memory
    standalone_question: str
    intent: Intent
    query: str
    retries: int
    retrieved: list[dict]
    relevant: list[dict]
    tool_calls: list[dict]
    tool_summary: str

    # outputs
    answer: str
    citations: list[dict]
    gap_reported: bool
    # every node appends one entry, so the client can show the reasoning trace
    steps: Annotated[list[dict], operator.add]
