import type {
  LoginRequest,
  TokenResponse,
  UserResponse,
} from "../../api/generated/model";
import { apiRequest, setAccessToken } from "../../api/client/http-client";

function storeAccessToken(session: TokenResponse): TokenResponse {
  if (!session.accessToken)
    throw new Error("Authentication response did not include an access token");
  setAccessToken(session.accessToken);
  return session;
}

export async function login(request: LoginRequest): Promise<TokenResponse> {
  const session = await apiRequest<TokenResponse>(
    "/api/auth/login",
    { method: "POST", body: JSON.stringify(request) },
    "public",
  );
  return storeAccessToken(session);
}

export async function restoreSession(): Promise<TokenResponse> {
  const session = await apiRequest<TokenResponse>(
    "/api/auth/refresh",
    { method: "POST" },
    "refresh",
  );
  return storeAccessToken(session);
}

export function getCurrentUser(): Promise<UserResponse> {
  return apiRequest<UserResponse>("/api/auth/me");
}

export function logoutSession(): Promise<void> {
  return apiRequest<void>("/api/auth/logout", { method: "POST" }, "public");
}
