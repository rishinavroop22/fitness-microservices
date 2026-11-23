import { Alert, Box, Button, Paper, Tab, Tabs, Typography } from "@mui/material";
import { useCallback, useEffect, useState } from "react";
import { useApi } from "../api.js";

/** Questions the agent could not answer, reported automatically through the MCP report_knowledge_gap tool. */
export default function GapsPage() {
  const api = useApi();
  const [status, setStatus] = useState("OPEN");
  const [gaps, setGaps] = useState([]);
  const [error, setError] = useState(null);

  const load = useCallback(() => api.gaps(status).then(setGaps).catch((e) => setError(e.message)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [status]);
  useEffect(() => { load(); }, [load]);

  async function resolve(id) {
    const note = window.prompt("What resolved this gap? (e.g. the document you uploaded)") ?? "";
    try { await api.resolveGap(id, note); load(); } catch (e) { setError(e.message); }
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1000, mx: "auto" }}>
      <Typography variant="h5" gutterBottom>Knowledge gaps</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        When the agent cannot find an answer after rewriting its search, it reports the question here so document
        owners know what to add.
      </Typography>
      <Tabs value={status} onChange={(_, v) => setStatus(v)} sx={{ mb: 2 }}>
        <Tab value="OPEN" label="Open" /><Tab value="RESOLVED" label="Resolved" />
      </Tabs>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {gaps.length === 0 && <Typography color="text.secondary">Nothing here.</Typography>}
      {gaps.map((g) => (
        <Paper key={g.id} sx={{ p: 2, mb: 1.5, display: "flex", gap: 2, alignItems: "flex-start" }}>
          <Box sx={{ flex: 1 }}>
            <Typography fontWeight={600}>{g.question}</Typography>
            <Typography variant="caption" color="text.secondary">
              {new Date(g.createdAt).toLocaleString()} · {g.reason}
              {g.resolutionNote && ` · Resolved: ${g.resolutionNote}`}
            </Typography>
          </Box>
          {g.status === "OPEN" && <Button size="small" variant="outlined" onClick={() => resolve(g.id)}>Mark resolved</Button>}
        </Paper>
      ))}
    </Box>
  );
}
