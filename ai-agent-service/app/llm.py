"""Factories for the chat model and the embedding model.

Gemini is used in every real deployment. The "fake" provider exists so the
whole service (ingestion, retrieval, the LangGraph workflow) can run offline
and in CI without an API key.
"""

from langchain_core.embeddings import Embeddings
from langchain_core.language_models import BaseChatModel

from app.config import Settings


def build_chat_model(settings: Settings) -> BaseChatModel:
    if settings.llm_provider == "fake":
        from app.fake_llm import RuleBasedFakeChatModel

        return RuleBasedFakeChatModel()

    from langchain_google_genai import ChatGoogleGenerativeAI

    return ChatGoogleGenerativeAI(
        model=settings.gemini_chat_model,
        api_key=settings.gemini_api_key or None,
        temperature=settings.llm_temperature,
    )


def build_embeddings(settings: Settings) -> Embeddings:
    if settings.llm_provider == "fake":
        from app.fake_llm import HashingEmbeddings

        return HashingEmbeddings()

    from langchain_google_genai import GoogleGenerativeAIEmbeddings

    return GoogleGenerativeAIEmbeddings(
        model=settings.gemini_embedding_model,
        api_key=settings.gemini_api_key or None,
    )
