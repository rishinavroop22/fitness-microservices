import { CssBaseline, ThemeProvider } from "@mui/material";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuthProvider } from "react-oauth2-code-pkce";
import { BrowserRouter } from "react-router-dom";
import App from "./App.jsx";
import { authConfig } from "./authConfig.js";
import "./index.css";
import { theme } from "./theme.js";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AuthProvider authConfig={authConfig}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </ThemeProvider>
    </AuthProvider>
  </StrictMode>,
);
