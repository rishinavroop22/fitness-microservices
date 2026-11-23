from app.agent.graph import parse_json


async def test_knowledge_question_retrieves_grades_and_cites(agent):
    result = await agent.run("How many unused annual leave days can be carried forward?", user_id="u1")
    assert result["intent"] == "knowledge"
    assert result["relevant"], "grader should keep the leave policy chunk"
    assert result["citations"][0]["documentId"] == "doc-leave"
    assert "[1]" in result["answer"]
    nodes = [s["node"] for s in result["steps"]]
    assert nodes == ["contextualize", "classify_intent", "retrieve", "grade_documents", "generate_answer"]


async def test_unanswerable_question_rewrites_then_reports_gap_over_mcp(agent, recorder):
    result = await agent.run("Quarterly carbon emissions of the Lisbon datacenter", user_id="u7")
    nodes = [s["node"] for s in result["steps"]]
    assert nodes.count("rewrite_query") == 2
    assert nodes[-1] == "report_gap"
    assert result["gap_reported"] is True
    assert ("report_knowledge_gap", {"question": "Quarterly carbon emissions of the Lisbon datacenter",
                                     "userId": "u7"}) in recorder.calls
    assert result["citations"] == []


async def test_catalog_question_uses_enterprise_tools(agent, recorder):
    result = await agent.run("Which documents do we have about leave?", user_id="u1")
    assert result["intent"] == "catalog"
    assert result["tool_calls"][0]["tool"] == "search_document_catalog"
    assert recorder.calls[0][0] == "search_document_catalog"
    assert "generate_answer" in [s["node"] for s in result["steps"]]


async def test_tool_user_id_is_forced_to_the_caller(agent, recorder):
    result = await agent.run("Show my profile", user_id="alice")
    call = result["tool_calls"][0]
    assert call["tool"] == "get_user_profile"
    assert call["args"]["userId"] == "alice"


async def test_department_scoping_applies_to_agent(agent):
    result = await agent.run("How do I connect to the VPN with multi-factor sign in?", department="HR")
    assert all(c["documentId"] != "doc-vpn" for c in result.get("citations", []))


async def test_smalltalk_skips_retrieval(agent):
    result = await agent.run("hello")
    assert result["intent"] == "smalltalk"
    assert [s["node"] for s in result["steps"]][-1] == "respond_smalltalk"


async def test_history_triggers_contextualization(agent):
    history = [{"role": "user", "content": "Tell me about annual leave"},
               {"role": "assistant", "content": "Employees get 24 days."}]
    result = await agent.run("How many annual leave days carry forward?", history=history)
    assert result["standalone_question"]
    assert result["citations"]


async def test_no_tools_available_degrades_gracefully(kb):
    from app.agent.graph import KnowledgeAgent
    from app.fake_llm import RuleBasedFakeChatModel

    async def no_tools():
        return []

    agent = KnowledgeAgent(RuleBasedFakeChatModel(), kb, no_tools, max_rewrites=1)
    result = await agent.run("Which documents do we have about leave?")
    assert result["tool_calls"] == []
    gap = await agent.run("Quarterly carbon emissions of the Lisbon datacenter")
    assert gap["gap_reported"] is False
    assert "knowledge gap" not in gap["answer"]


def test_parse_json_handles_fences_and_prose():
    assert parse_json('Sure!\n```json\n{"intent": "catalog"}\n```') == {"intent": "catalog"}
    assert parse_json("no json here") is None


async def test_tool_department_is_forced_to_the_callers_department(agent):
    result = await agent.run("Which documents do we have about leave?", user_id="u1", department="HR")
    assert result["tool_calls"][0]["args"]["department"] == "HR"
