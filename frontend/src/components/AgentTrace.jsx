import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { Accordion, AccordionDetails, AccordionSummary, Box, Typography } from "@mui/material";

const LABELS = {
  contextualize: "Rewrote follow-up as a standalone question",
  classify_intent: "Classified intent",
  retrieve: "Searched the knowledge base",
  grade_documents: "Graded passages for relevance",
  rewrite_query: "Rewrote the search query",
  enterprise_tools: "Called enterprise tools over MCP",
  generate_answer: "Generated a cited answer",
  report_gap: "Logged a knowledge gap",
  respond_smalltalk: "Replied directly",
  agent_unavailable: "Agent unavailable",
};

function detail(step) {
  switch (step.node) {
    case "classify_intent": return step.intent;
    case "retrieve": return `${step.hits} passages for “${step.query}”`;
    case "grade_documents": return `${step.relevant} of ${step.graded} relevant`;
    case "rewrite_query": return `“${step.query}”`;
    case "enterprise_tools": return step.tools?.length ? step.tools.join(", ") : `${step.available} tools available`;
    case "generate_answer": return `${step.cited} sources cited`;
    case "report_gap": return step.reported ? "reported" : "not reported";
    default: return "";
  }
}

/** Shows the LangGraph nodes that produced an answer. */
export default function AgentTrace({ steps, latencyMs }) {
  if (!steps?.length) return null;
  return (
    <Accordion disableGutters elevation={0} sx={{ mt: 1, border: "none", "&:before": { display: "none" },
                                                  bgcolor: "transparent" }}>
      <AccordionSummary expandIcon={<ExpandMoreIcon fontSize="small" />} sx={{ px: 0, minHeight: 0,
                        "& .MuiAccordionSummary-content": { my: 0.5 } }}>
        <Typography variant="caption" color="text.secondary">
          How this answer was produced · {steps.length} steps{latencyMs ? ` · ${(latencyMs / 1000).toFixed(1)}s` : ""}
        </Typography>
      </AccordionSummary>
      <AccordionDetails sx={{ px: 0, pt: 0 }}>
        {steps.map((s, i) => (
          <Box key={i} sx={{ display: "flex", gap: 1, fontSize: 13, py: 0.25 }}>
            <Typography variant="caption" sx={{ width: 18, color: "text.disabled" }}>{i + 1}</Typography>
            <Typography variant="caption" sx={{ flex: 1 }}>
              {LABELS[s.node] ?? s.node}
              {detail(s) && <Box component="span" sx={{ color: "text.secondary" }}> — {detail(s)}</Box>}
            </Typography>
            {s.ms !== undefined && <Typography variant="caption" color="text.disabled">{s.ms} ms</Typography>}
          </Box>
        ))}
      </AccordionDetails>
    </Accordion>
  );
}
