import { useCallback, useContext } from "react";
import { AuthContext } from "react-oauth2-code-pkce";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

async function request(token, path, { method = "GET", body, form } = {}) {
  const headers = { Authorization: `Bearer ${token}` };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers,
    body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });
  if (!response.ok) {
    let detail = response.statusText;
    try {
      const problem = await response.json();
      detail = problem.detail ?? problem.message ?? detail;
    } catch {
      /* body was not JSON */
    }
    throw new Error(`${response.status}: ${detail}`);
  }
  return response.status === 204 ? null : response.json();
}

/** API client bound to the current Keycloak access token. */
export function useApi() {
  const { token } = useContext(AuthContext);
  const call = useCallback((path, options) => request(token, path, options), [token]);

  return {
    conversations: () => call("/api/chat/conversations"),
    messages: (id) => call(`/api/chat/conversations/${id}/messages`),
    ask: (question, conversationId) => call("/api/chat/ask", { method: "POST", body: { question, conversationId } }),
    deleteConversation: (id) => call(`/api/chat/conversations/${id}`, { method: "DELETE" }),

    documents: (q = "") => call(`/api/documents?limit=100&q=${encodeURIComponent(q)}`),
    uploadDocument: (form) => call("/api/documents", { method: "POST", form }),
    reindexDocument: (id) => call(`/api/documents/${id}/reindex`, { method: "POST" }),
    deleteDocument: (id) => call(`/api/documents/${id}`, { method: "DELETE" }),

    gaps: (status = "OPEN") => call(`/api/knowledge-gaps?status=${status}`),
    resolveGap: (id, resolutionNote) =>
      call(`/api/knowledge-gaps/${id}/resolve`, { method: "PATCH", body: { resolutionNote } }),

    me: () => call("/api/users/me"),
  };
}
