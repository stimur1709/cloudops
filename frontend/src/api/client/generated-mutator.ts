import { apiRequestWithResponse, type RequestMode } from "./http-client";

function requestMode(url: string): RequestMode {
  if (url === "/api/auth/refresh") return "refresh";
  if (
    ["/api/auth/login", "/api/auth/logout", "/api/auth/register"].includes(url)
  )
    return "public";
  return "protected";
}

export async function generatedRequest<T>(
  url: string,
  options?: RequestInit,
): Promise<T> {
  const response = await apiRequestWithResponse<unknown>(
    url,
    options,
    requestMode(url),
  );
  return response as T;
}
