import DeleteIcon from "@mui/icons-material/DeleteOutline";
import RefreshIcon from "@mui/icons-material/Refresh";
import UploadIcon from "@mui/icons-material/UploadFile";
import {
  Alert, Box, Button, Chip, FormControlLabel, IconButton, Paper, Switch, Table, TableBody, TableCell, TableHead,
  TableRow, TextField, Tooltip, Typography,
} from "@mui/material";
import { useCallback, useEffect, useState } from "react";
import { useApi } from "../api.js";

const STATUS_COLOR = { INDEXED: "success", PROCESSING: "info", FAILED: "error" };
const ACCEPT = ".pdf,.docx,.doc,.pptx,.txt,.md,.html,.htm,.rtf,.odt,.csv";

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export default function DocumentsPage() {
  const api = useApi();
  const [documents, setDocuments] = useState([]);
  const [query, setQuery] = useState("");
  const [file, setFile] = useState(null);
  const [title, setTitle] = useState("");
  const [tags, setTags] = useState("");
  const [companyWide, setCompanyWide] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(() => api.documents(query).then(setDocuments).catch((e) => setError(e.message)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [query]);

  useEffect(() => { load(); }, [load]);

  // Poll while the AI service is still embedding something.
  useEffect(() => {
    if (!documents.some((d) => d.status === "PROCESSING")) return undefined;
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [documents, load]);

  async function upload(e) {
    e.preventDefault();
    if (!file) return;
    const form = new FormData();
    form.append("file", file);
    if (title.trim()) form.append("title", title.trim());
    if (tags.trim()) form.append("tags", tags.trim());
    if (companyWide) form.append("department", "ALL");
    setBusy(true);
    setError(null);
    try {
      await api.uploadDocument(form);
      setFile(null); setTitle(""); setTags("");
      e.target.reset();
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function act(fn) {
    try { await fn(); load(); } catch (err) { setError(err.message); }
  }

  return (
    <Box sx={{ p: 3, maxWidth: 1200, mx: "auto" }}>
      <Typography variant="h5" gutterBottom>Knowledge base</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        Uploaded files are parsed, queued on RabbitMQ, chunked and embedded into ChromaDB. They become searchable
        once their status is Indexed.
      </Typography>

      <Paper component="form" onSubmit={upload} sx={{ p: 2, mb: 3, display: "flex", gap: 2, flexWrap: "wrap",
                                                      alignItems: "center" }}>
        <Button variant="outlined" component="label" startIcon={<UploadIcon />}>
          {file ? file.name : "Choose file"}
          <input hidden type="file" accept={ACCEPT} onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        </Button>
        <TextField size="small" label="Title (optional)" value={title} onChange={(e) => setTitle(e.target.value)} />
        <TextField size="small" label="Tags, comma separated" value={tags} onChange={(e) => setTags(e.target.value)} />
        <FormControlLabel control={<Switch checked={companyWide} onChange={(e) => setCompanyWide(e.target.checked)} />}
                          label="Share company-wide" />
        <Button type="submit" variant="contained" disabled={!file || busy}>{busy ? "Uploading…" : "Upload"}</Button>
      </Paper>

      {error && <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 2 }}>{error}</Alert>}

      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
        <TextField size="small" placeholder="Filter by title, file or tag" value={query}
                   onChange={(e) => setQuery(e.target.value)} sx={{ width: 320 }} />
      </Box>
      <Paper>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Title</TableCell><TableCell>Visibility</TableCell><TableCell>Tags</TableCell>
              <TableCell>Size</TableCell><TableCell>Chunks</TableCell><TableCell>Status</TableCell>
              <TableCell>Uploaded</TableCell><TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {documents.length === 0 && (
              <TableRow><TableCell colSpan={8} sx={{ textAlign: "center", color: "text.secondary", py: 4 }}>
                No documents yet. Upload a policy, handbook or spec to get started.
              </TableCell></TableRow>
            )}
            {documents.map((d) => (
              <TableRow key={d.id} hover>
                <TableCell>
                  <Typography variant="body2" fontWeight={600}>{d.title}</Typography>
                  <Typography variant="caption" color="text.secondary">{d.fileName}</Typography>
                </TableCell>
                <TableCell>{d.department === "ALL" ? "Company-wide" : d.department}</TableCell>
                <TableCell>{d.tags.map((t) => <Chip key={t} label={t} size="small" sx={{ mr: 0.5 }} />)}</TableCell>
                <TableCell>{formatSize(d.sizeBytes)}</TableCell>
                <TableCell>{d.chunkCount || "—"}</TableCell>
                <TableCell>
                  <Tooltip title={d.errorMessage ?? ""}>
                    <Chip size="small" color={STATUS_COLOR[d.status]} label={d.status.toLowerCase()} />
                  </Tooltip>
                </TableCell>
                <TableCell>{new Date(d.createdAt).toLocaleDateString()}</TableCell>
                <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                  <Tooltip title="Re-index"><IconButton size="small" onClick={() => act(() => api.reindexDocument(d.id))}>
                    <RefreshIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Delete"><IconButton size="small" onClick={() => act(() => api.deleteDocument(d.id))}>
                    <DeleteIcon fontSize="small" /></IconButton></Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Box>
  );
}
