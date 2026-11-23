from tests.conftest import LEAVE_POLICY


def test_index_and_semantic_search_ranks_relevant_document_first(kb):
    hits = kb.search("How many annual leave days can be carried forward?", k=3)
    assert hits, "expected results"
    assert hits[0].document_id == "doc-leave"
    assert hits[0].title == "Annual Leave Policy"


def test_department_filter_hides_other_departments(kb):
    hr_hits = kb.search("VPN multi-factor sign in", k=5, department="HR")
    assert all(h.document_id != "doc-vpn" for h in hr_hits)
    eng_hits = kb.search("VPN multi-factor sign in", k=5, department="engineering")
    assert eng_hits[0].document_id == "doc-vpn"


def test_reindex_replaces_old_chunks_and_delete_removes_them(kb):
    before = kb.count()
    kb.index_document("doc-leave", "Annual Leave Policy", LEAVE_POLICY)
    assert kb.count() == before
    kb.delete_document("doc-leave")
    assert all(h.document_id != "doc-leave" for h in kb.search("annual leave", k=10))


def test_chunking_respects_size(kb):
    chunks = kb.split("word " * 1000)
    assert len(chunks) > 1
    assert all(len(c) <= 400 for c in chunks)
