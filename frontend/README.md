# Knowledge Assistant UI

React + Vite + MUI client. Signs in with Keycloak (OIDC authorization code + PKCE) and talks only to the API gateway.

```bash
cp .env.example .env   # adjust URLs if needed
npm install
npm run dev            # http://localhost:5173
```

Pages: **Assistant** (chat with citations and the agent's step trace), **Documents** (upload, status, re-index, delete) and **Knowledge gaps** (questions the agent could not answer).
