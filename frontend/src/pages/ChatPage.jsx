import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/DeleteOutline";
import SendIcon from "@mui/icons-material/Send";
import {
  Alert, Box, Button, Chip, CircularProgress, IconButton, List, ListItemButton, ListItemText, Paper,
  TextField, Typography,
} from "@mui/material";
import { useCallback, useEffect, useRef, useState } from "react";
import Markdown from "react-markdown";
import { useNavigate, useParams } from "react-router-dom";
import { useApi } from "../api.js";
import AgentTrace from "../components/AgentTrace.jsx";
import Citations from "../components/Citations.jsx";

const SUGGESTIONS = [
  "How many days of annual leave can I carry forward?",
  "Which documents do we have about security?",
  "What is the process for requesting VPN access?",
];

function Message({ message }) {
  const isUser = message.role === "USER";
  return (
    <Box sx={{ display: "flex", justifyContent: isUser ? "flex-end" : "flex-start", mb: 2 }}>
      <Paper sx={{ px: 2, py: 1.5, maxWidth: "78%", bgcolor: isUser ? "primary.main" : "background.paper",
                   color: isUser ? "primary.contrastText" : "text.primary", borderColor: isUser ? "primary.main" : undefined }}>
        {isUser ? (
          <Typography sx={{ whiteSpace: "pre-wrap" }}>{message.content}</Typography>
        ) : (
          <>
            <Box className="markdown"><Markdown>{message.content}</Markdown></Box>
            {message.gapReported && (
              <Chip size="small" color="warning" variant="outlined" label="Logged as a knowledge gap" sx={{ mt: 1 }} />
            )}
            <Citations citations={message.citations} />
            <AgentTrace steps={message.steps} latencyMs={message.latencyMs} />
          </>
        )}
      </Paper>
    </Box>
  );
}

export default function ChatPage() {
  const api = useApi();
  const navigate = useNavigate();
  const { conversationId } = useParams();
  const [conversations, setConversations] = useState([]);
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const bottomRef = useRef(null);

  const loadConversations = useCallback(() => api.conversations().then(setConversations).catch((e) => setError(e.message)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []);

  useEffect(() => { loadConversations(); }, [loadConversations]);

  useEffect(() => {
    const load = conversationId ? api.messages(conversationId) : Promise.resolve([]);
    load.then(setMessages).catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId]);

  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages, sending]);

  async function send(text) {
    const q = (text ?? question).trim();
    if (!q || sending) return;
    setQuestion("");
    setError(null);
    setSending(true);
    setMessages((m) => [...m, { id: "pending", role: "USER", content: q }]);
    try {
      const exchange = await api.ask(q, conversationId);
      setMessages((m) => [...m.filter((x) => x.id !== "pending"), exchange.question, exchange.answer]);
      if (exchange.conversation.id !== conversationId) navigate(`/chat/${exchange.conversation.id}`, { replace: true });
      loadConversations();
    } catch (e) {
      setError(e.message);
      setMessages((m) => m.filter((x) => x.id !== "pending"));
      setQuestion(q);
    } finally {
      setSending(false);
    }
  }

  async function remove(id) {
    await api.deleteConversation(id);
    if (id === conversationId) navigate("/chat");
    loadConversations();
  }

  return (
    <Box sx={{ display: "flex", height: "100%" }}>
      <Box sx={{ width: 280, borderRight: "1px solid #e3e6eb", bgcolor: "background.paper", display: "flex",
                 flexDirection: "column" }}>
        <Box sx={{ p: 1.5 }}>
          <Button fullWidth variant="outlined" startIcon={<AddIcon />} onClick={() => navigate("/chat")}>
            New conversation
          </Button>
        </Box>
        <List dense sx={{ overflowY: "auto", flex: 1 }}>
          {conversations.map((c) => (
            <ListItemButton key={c.id} selected={c.id === conversationId} onClick={() => navigate(`/chat/${c.id}`)}
                            sx={{ "&:hover .del": { visibility: "visible" } }}>
              <ListItemText primary={c.title} primaryTypographyProps={{ noWrap: true }}
                            secondary={new Date(c.updatedAt).toLocaleDateString()} />
              <IconButton className="del" size="small" sx={{ visibility: "hidden" }}
                          onClick={(e) => { e.stopPropagation(); remove(c.id); }}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </ListItemButton>
          ))}
        </List>
      </Box>

      <Box sx={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <Box sx={{ flex: 1, overflowY: "auto", p: 3 }}>
          <Box sx={{ maxWidth: 860, mx: "auto" }}>
            {messages.length === 0 && !sending && (
              <Box sx={{ textAlign: "center", mt: 8 }}>
                <Typography variant="h5" gutterBottom>What do you need to know?</Typography>
                <Typography color="text.secondary" sx={{ mb: 3 }}>
                  Answers come from documents your department can access, with a citation for every source.
                </Typography>
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, justifyContent: "center" }}>
                  {SUGGESTIONS.map((s) => <Chip key={s} label={s} onClick={() => send(s)} variant="outlined" />)}
                </Box>
              </Box>
            )}
            {messages.map((m) => <Message key={m.id} message={m} />)}
            {sending && (
              <Box sx={{ display: "flex", alignItems: "center", gap: 1, color: "text.secondary" }}>
                <CircularProgress size={16} /> <Typography variant="body2">Searching the knowledge base…</Typography>
              </Box>
            )}
            <div ref={bottomRef} />
          </Box>
        </Box>
        <Box sx={{ p: 2, borderTop: "1px solid #e3e6eb", bgcolor: "background.paper" }}>
          <Box sx={{ maxWidth: 860, mx: "auto" }}>
            {error && <Alert severity="error" onClose={() => setError(null)} sx={{ mb: 1 }}>{error}</Alert>}
            <Box component="form" onSubmit={(e) => { e.preventDefault(); send(); }} sx={{ display: "flex", gap: 1 }}>
              <TextField fullWidth multiline maxRows={6} size="small" placeholder="Ask about policies, procedures, specs…"
                         value={question} onChange={(e) => setQuestion(e.target.value)}
                         onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); } }} />
              <Button type="submit" variant="contained" disabled={sending || !question.trim()} endIcon={<SendIcon />}>
                Ask
              </Button>
            </Box>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
