"""Prompt templates for every LLM step of the agent workflow.

Every prompt starts with a ``TASK:`` line. Gemini ignores it, and the offline
fake model uses it to decide which step it is answering.
"""

CONTEXTUALIZE = """TASK: CONTEXTUALIZE
You rewrite a follow-up question into a standalone question.
Use the conversation history only to resolve references such as "it", "that policy" or "the second one".
If the question is already standalone, return it unchanged.
Return only the rewritten question, with no explanation.

Conversation history:
{history}

Follow-up question: {question}
Standalone question:"""


CLASSIFY_INTENT = """TASK: CLASSIFY_INTENT
Classify the user's message for an enterprise knowledge assistant.

Categories:
- "knowledge": a question whose answer lives inside company documents (policies, procedures, specs, handbooks, reports).
- "catalog": a question about the document library itself or about people/records in enterprise systems,
  e.g. "which documents do we have about onboarding?", "who uploaded the security policy?", "show me my profile".
- "smalltalk": greetings, thanks, or questions about the assistant itself.

Return JSON only: {{"intent": "<knowledge|catalog|smalltalk>", "reason": "<short reason>"}}

Message: {question}"""


GRADE_DOCUMENTS = """TASK: GRADE_DOCUMENTS
You are grading retrieved document chunks for relevance to a question.
A chunk is relevant if it contains information that helps answer the question, even partially.

Question: {question}

Chunks:
{chunks}

Return JSON only: {{"relevant": [<numbers of the relevant chunks>]}}"""


REWRITE_QUERY = """TASK: REWRITE_QUERY
The search below found no relevant passages in the company knowledge base.
Rewrite the query so a semantic search is more likely to succeed: expand acronyms, add likely synonyms,
and use the vocabulary a policy or technical document would use.
Return only the new query.

Original question: {question}
Previous query: {query}
New query:"""


TOOL_AGENT_SYSTEM = """TASK: TOOL_AGENT
You are the enterprise tools step of a knowledge assistant. Use the available tools to look up
document-catalog and user information needed to answer the question. Call tools as needed, then reply
with a short factual summary of what you found. Never invent records the tools did not return.
The current user id is {user_id}."""


GENERATE_ANSWER = """TASK: GENERATE_ANSWER
You are an enterprise knowledge assistant. Answer the question using ONLY the context below.
Cite sources inline with their bracket numbers, e.g. [1] or [2][3], after every claim that uses them.
If the context does not contain the answer, say so plainly instead of guessing.
Be concise and well structured; use short bullet lists for procedures.

Question: {question}

Context:
{context}

Answer:"""


NO_ANSWER = """I couldn't find this in the knowledge base{scope}. I've logged it as a knowledge gap so the \
document owners can add the missing information. You could try rephrasing the question or asking about a \
related policy or document."""


SMALLTALK = """TASK: SMALLTALK
You are a friendly enterprise knowledge assistant. Reply briefly to the message below.
If relevant, mention that you can answer questions from the company's documents and look up the document catalog.

Message: {question}"""
