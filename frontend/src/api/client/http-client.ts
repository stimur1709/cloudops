import type { TokenResponse } from "../generated/model";
import { ApiClientError, mapResponseError } from "./api-error";

export type RequestMode = "protected" | "public" | "refresh";

export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim() ?? "";
export const apiBaseUrl = configuredBaseUrl.replace(/\/$/, "");

let accessToken: string | null = null;
let refreshPromise: Promise<string> | null = null;
let sessionExpiredHandler: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessTokenForTests() {
  return accessToken;
}

export function onSessionExpired(handler: (() => void) | null) {
  sessionExpiredHandler = handler;
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

async function rawRequest<T>(
  path: string,
  init: RequestInit,
  mode: RequestMode,
): Promise<ApiResponse<T>> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type"))
    headers.set("Content-Type", "application/json");
  if (mode === "protected" && accessToken)
    headers.set("Authorization", `Bearer ${accessToken}`);

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers,
      credentials: "include",
    });
  } catch {
    throw new ApiClientError("Network request failed", { kind: "network" });
  }

  const data = await parseBody(response);
  if (!response.ok) throw mapResponseError(response.status, data);
  return {
    data: data as T,
    status: response.status,
    headers: response.headers,
  };
}

export async function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = rawRequest<TokenResponse>(
      "/api/auth/refresh",
      { method: "POST" },
      "refresh",
    )
      .then(({ data: session }) => {
        if (!session.accessToken)
          throw new ApiClientError(
            "Refresh response did not include an access token",
            { kind: "unexpected" },
          );
        setAccessToken(session.accessToken);
        return session.accessToken;
      })
      .catch((error: unknown) => {
        setAccessToken(null);
        sessionExpiredHandler?.();
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function apiRequestWithResponse<T>(
  path: string,
  init: RequestInit = {},
  mode: RequestMode = "protected",
): Promise<ApiResponse<T>> {
  try {
    return await rawRequest<T>(path, init, mode);
  } catch (error) {
    if (
      mode !== "protected" ||
      !(error instanceof ApiClientError) ||
      error.status !== 401
    )
      throw error;
    await refreshAccessToken();
    return rawRequest<T>(path, init, "protected");
  }
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  mode: RequestMode = "protected",
): Promise<T> {
  const response = await apiRequestWithResponse<T>(path, init, mode);
  return response.data;
}

export function resetHttpClientForTests() {
  accessToken = null;
  refreshPromise = null;
  sessionExpiredHandler = null;
}
