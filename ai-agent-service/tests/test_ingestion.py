from app.ingestion import handle_ingest


def test_handle_ingest_indexes_and_reports_status(kb):
    status = handle_ingest(kb, {"documentId": "doc-new", "title": "Expense Policy",
                                "content": "Meals are reimbursed up to 40 USD per day when travelling.",
                                "department": "finance", "tags": ["travel"]})
    assert status == {"documentId": "doc-new", "status": "INDEXED", "chunkCount": 1, "error": None}
    assert kb.search("meal reimbursement travelling", k=1, department="FINANCE")[0].document_id == "doc-new"


def test_handle_ingest_reports_failure_for_empty_document(kb):
    status = handle_ingest(kb, {"documentId": "doc-empty", "title": "Scan", "content": "   "})
    assert status["status"] == "FAILED"
    assert "no extractable text" in status["error"]
