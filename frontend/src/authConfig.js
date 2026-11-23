const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8181";
const realm = import.meta.env.VITE_KEYCLOAK_REALM ?? "knowledge-assistant";
const base = `${keycloakUrl}/realms/${realm}/protocol/openid-connect`;

export const authConfig = {
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "knowledge-assistant-ui",
  authorizationEndpoint: `${base}/auth`,
  tokenEndpoint: `${base}/token`,
  logoutEndpoint: `${base}/logout`,
  redirectUri: window.location.origin,
  scope: "openid profile email",
  autoLogin: false,
  decodeToken: true,
};
