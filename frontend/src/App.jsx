import ChatIcon from "@mui/icons-material/ChatBubbleOutline";
import DescriptionIcon from "@mui/icons-material/DescriptionOutlined";
import HelpIcon from "@mui/icons-material/HelpOutline";
import { AppBar, Box, Button, Paper, Stack, Tab, Tabs, Toolbar, Typography } from "@mui/material";
import { useContext } from "react";
import { AuthContext } from "react-oauth2-code-pkce";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import ChatPage from "./pages/ChatPage.jsx";
import DocumentsPage from "./pages/DocumentsPage.jsx";
import GapsPage from "./pages/GapsPage.jsx";

const NAV = [
  { path: "/chat", label: "Assistant", icon: <ChatIcon fontSize="small" /> },
  { path: "/documents", label: "Documents", icon: <DescriptionIcon fontSize="small" /> },
  { path: "/gaps", label: "Knowledge gaps", icon: <HelpIcon fontSize="small" /> },
];

function Login({ onLogin }) {
  return (
    <Box sx={{ height: "100%", display: "grid", placeItems: "center", p: 2 }}>
      <Paper sx={{ p: 5, maxWidth: 460, textAlign: "center" }}>
        <Typography variant="h5" gutterBottom>Enterprise Knowledge Assistant</Typography>
        <Typography color="text.secondary" sx={{ mb: 3 }}>
          Ask questions in plain language and get answers grounded in your company's documents, with
          citations to every source.
        </Typography>
        <Button variant="contained" size="large" onClick={() => onLogin()}>Sign in with company SSO</Button>
      </Paper>
    </Box>
  );
}

export default function App() {
  const { token, tokenData, logIn, logOut, loginInProgress } = useContext(AuthContext);
  const location = useLocation();
  const navigate = useNavigate();

  if (loginInProgress) return null;
  if (!token) return <Login onLogin={logIn} />;

  const current = NAV.find((n) => location.pathname.startsWith(n.path))?.path ?? false;
  const name = tokenData?.given_name ?? tokenData?.preferred_username ?? "";
  const department = tokenData?.department ?? "ALL";

  return (
    <Box sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <AppBar position="static" color="primary">
        <Toolbar variant="dense" sx={{ gap: 2 }}>
          <Typography variant="h6" sx={{ mr: 2, whiteSpace: "nowrap" }}>Knowledge Assistant</Typography>
          <Tabs value={current} onChange={(_, v) => navigate(v)} textColor="inherit"
                TabIndicatorProps={{ style: { backgroundColor: "#f4b942" } }} sx={{ flexGrow: 1 }}>
            {NAV.map((n) => (
              <Tab key={n.path} value={n.path} label={n.label} icon={n.icon} iconPosition="start"
                   sx={{ minHeight: 48, textTransform: "none" }} />
            ))}
          </Tabs>
          <Stack direction="row" spacing={2} alignItems="center">
            <Typography variant="body2" sx={{ opacity: 0.85 }}>{name} · {department}</Typography>
            <Button color="inherit" variant="outlined" size="small" onClick={() => logOut()}>Sign out</Button>
          </Stack>
        </Toolbar>
      </AppBar>
      <Box sx={{ flex: 1, minHeight: 0 }}>
        <Routes>
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/chat/:conversationId" element={<ChatPage />} />
          <Route path="/documents" element={<DocumentsPage />} />
          <Route path="/gaps" element={<GapsPage />} />
          <Route path="*" element={<Navigate to="/chat" replace />} />
        </Routes>
      </Box>
    </Box>
  );
}
