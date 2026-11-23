from fastapi.testclient import TestClient

from app.main import create_app
from tests.conftest import LEAVE_POLICY


def test_index_then_ask_over_http(settings):
    with TestClient(create_app(settings, start_consumer=False)) as client:
        assert client.get("/health").json()["status"] == "UP"
        r = client.post("/knowledge/index", json={"documentId": "d1", "title": "Annual Leave Policy",
                                                   "content": LEAVE_POLICY})
        assert r.status_code == 200 and r.json()["status"] == "INDEXED"

        r = client.post("/agent/invoke", json={"question": "How many annual leave days carry forward?",
                                                "userId": "u1"})
        body = r.json()
        assert r.status_code == 200
        assert body["intent"] == "knowledge"
        assert body["citations"][0]["documentId"] == "d1"
        assert body["steps"][0]["node"] == "contextualize"

        assert client.post("/knowledge/search", json={"query": "annual leave"}).json()[0]["documentId"] == "d1"
        assert client.delete("/knowledge/d1").status_code == 200


def test_rejects_empty_question(settings):
    with TestClient(create_app(settings, start_consumer=False)) as client:
        assert client.post("/agent/invoke", json={"question": ""}).status_code == 422
