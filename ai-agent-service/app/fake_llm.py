"""Offline stand-ins for Gemini, used when LLM_PROVIDER=fake and in tests.

* ``HashingEmbeddings`` is a bag-of-words hashing embedding, so retrieval still
  ranks chunks by shared vocabulary without any network calls.
* ``RuleBasedFakeChatModel`` reads the ``TASK:`` line each prompt starts with
  and answers that step deterministically.
"""

from __future__ import annotations

import hashlib
import json
import math
import re
from typing import Any, Sequence

from langchain_core.callbacks import CallbackManagerForLLMRun
from langchain_core.embeddings import Embeddings
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage, ToolMessage
from langchain_core.outputs import ChatGeneration, ChatResult

_WORD = re.compile(r"[a-z0-9]+")
_STOPWORDS = {
    "the", "a", "an", "is", "are", "of", "to", "and", "or", "in", "on", "for", "what", "how", "do", "does",
    "i", "we", "our", "my", "me", "can", "with", "be", "it", "this", "that", "at", "by", "from", "as", "about",
    "which", "who", "when", "where", "why", "there", "any", "have", "has", "you", "your", "should", "will",
}


def tokenize(text: str) -> list[str]:
    return [w for w in _WORD.findall(text.lower()) if w not in _STOPWORDS and len(w) > 1]


class HashingEmbeddings(Embeddings):
    def __init__(self, size: int = 512):
        self.size = size

    def _embed(self, text: str) -> list[float]:
        vec = [0.0] * self.size
        for token in tokenize(text):
            # crude stemming so "policies" and "policy" share a bucket
            stem = token[:6]
            idx = int(hashlib.md5(stem.encode()).hexdigest(), 16) % self.size
            vec[idx] += 1.0
        norm = math.sqrt(sum(v * v for v in vec)) or 1.0
        return [v / norm for v in vec]

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(t) for t in texts]

    def embed_query(self, text: str) -> list[float]:
        return self._embed(text)


def _overlap(a: str, b: str) -> int:
    return len({t[:6] for t in tokenize(a)} & {t[:6] for t in tokenize(b)})


def _field(prompt: str, name: str) -> str:
    match = re.search(rf"^{name}:\s*(.*)$", prompt, flags=re.MULTILINE)
    return match.group(1).strip() if match else ""


class RuleBasedFakeChatModel(BaseChatModel):
    tools: list[Any] = []

    @property
    def _llm_type(self) -> str:
        return "rule-based-fake"

    def bind_tools(self, tools: Sequence[Any], **kwargs: Any) -> "RuleBasedFakeChatModel":
        return RuleBasedFakeChatModel(tools=list(tools))

    def _generate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        message = self._respond(messages)
        return ChatResult(generations=[ChatGeneration(message=message)])

    # ------------------------------------------------------------------
    def _respond(self, messages: list[BaseMessage]) -> AIMessage:
        prompt = "\n".join(str(m.content) for m in messages)
        task = _field(prompt, "TASK")

        if task == "CONTEXTUALIZE":
            return AIMessage(content=_field(prompt, "Follow-up question"))

        if task == "CLASSIFY_INTENT":
            q = _field(prompt, "Message").lower()
            if re.search(r"\b(hi|hello|hey|thanks|thank you|who are you)\b", q) and len(q.split()) <= 6:
                intent = "smalltalk"
            elif re.search(r"(which|what|list|show).*(documents?|files?|uploaded)|my profile|who uploaded", q):
                intent = "catalog"
            else:
                intent = "knowledge"
            return AIMessage(content=json.dumps({"intent": intent, "reason": "rule-based"}))

        if task == "GRADE_DOCUMENTS":
            question = _field(prompt, "Question")
            chunks = re.findall(r"^\[(\d+)\](.*?)(?=^\[\d+\]|\Z)", prompt.split("Chunks:", 1)[-1], re.M | re.S)
            relevant = [int(n) for n, text in chunks if _overlap(question, text) >= 2]
            return AIMessage(content=json.dumps({"relevant": relevant}))

        if task == "REWRITE_QUERY":
            return AIMessage(content=_field(prompt, "Original question") + " policy procedure guideline")

        if task == "TOOL_AGENT":
            return self._tool_step(messages)

        if task == "GENERATE_ANSWER":
            context = prompt.split("Context:", 1)[-1].rsplit("Answer:", 1)[0]
            first = re.search(r"\[(\d+)\][^\n]*\n(.+?)(?=\n\[\d+\]|\Z)", context, re.S)
            if not first:
                return AIMessage(content="The provided context does not contain the answer.")
            snippet = " ".join(first.group(2).split())[:400]
            return AIMessage(content=f"According to the knowledge base: {snippet} [{first.group(1)}]")

        if task == "SMALLTALK":
            return AIMessage(
                content="Hello! I can answer questions from your company's documents and look up the document catalog."
            )

        return AIMessage(content="")

    def _tool_step(self, messages: list[BaseMessage]) -> AIMessage:
        tool_results = [m for m in messages if isinstance(m, ToolMessage)]
        if tool_results:
            return AIMessage(content="Catalog lookup result: " + " ".join(str(m.content) for m in tool_results)[:800])
        names = {getattr(t, "name", None) for t in self.tools}
        question = str(messages[-1].content)
        if "get_user_profile" in names and "profile" in question.lower():
            user = re.search(r"current user id is (\S+?)\.?$", str(messages[0].content), re.M)
            call = {"name": "get_user_profile", "args": {"userId": user.group(1) if user else ""}, "id": "call_1"}
        elif "search_document_catalog" in names:
            call = {"name": "search_document_catalog", "args": {"query": " ".join(tokenize(question)[:4])}, "id": "call_1"}
        else:
            return AIMessage(content="No enterprise tools are available right now.")
        return AIMessage(content="", tool_calls=[call])
