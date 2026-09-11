import { afterEach, describe, expect, it, vi } from "vitest";
import {
  apiBaseUrl,
  apiRequest,
  getAccessTokenForTests,
  onSessionExpired,
  resetHttpClientForTests,
  setAccessToken,
} from "./http-client";

const session = {
  accessToken: "fresh-token",
  tokenType: "Bearer",
  expiresIn: 900,
  accessTokenExpiresAt: "2026-09-10T12:15:00Z",
  refreshTokenExpiresAt: "2026-09-17T12:00:00Z",
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
  resetHttpClientForTests();
});

describe("HTTP client", () => {
  it("uses the configured base URL and cookie credentials", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await apiRequest("/api/example", {}, "public");

    expect(fetchMock).toHaveBeenCalledWith(
      `${apiBaseUrl}/api/example`,
      expect.objectContaining({ credentials: "include" }),
    );
  });

  it("shares one refresh across concurrent 401 responses and retries each request once", async () => {
    setAccessToken("expired-token");
    let protectedCalls = 0;
    let refreshCalls = 0;
    const fetchMock = vi.fn(
      async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith("/api/auth/refresh")) {
          refreshCalls += 1;
          return jsonResponse(session);
        }
        protectedCalls += 1;
        if (protectedCalls <= 2)
          return jsonResponse(
            { code: "UNAUTHENTICATED", message: "Expired" },
            401,
          );
        expect(new Headers(init?.headers).get("Authorization")).toBe(
          "Bearer fresh-token",
        );
        return jsonResponse({ ok: true });
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      Promise.all([apiRequest("/api/a"), apiRequest("/api/b")]),
    ).resolves.toEqual([{ ok: true }, { ok: true }]);
    expect(refreshCalls).toBe(1);
    expect(protectedCalls).toBe(4);
    expect(getAccessTokenForTests()).toBe("fresh-token");
  });

  it("ends the session when refresh fails and never loops", async () => {
    const expired = vi.fn();
    onSessionExpired(expired);
    setAccessToken("expired-token");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({ code: "UNAUTHENTICATED", message: "Expired" }, 401),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          { code: "INVALID_REFRESH_TOKEN", message: "Invalid" },
          401,
        ),
      );
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiRequest("/api/protected")).rejects.toMatchObject({
      status: 401,
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(expired).toHaveBeenCalledOnce();
    expect(getAccessTokenForTests()).toBeNull();
  });

  it("does not send public auth failures through the refresh flow", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        jsonResponse(
          { code: "BAD_CREDENTIALS", message: "Bad credentials" },
          401,
        ),
      );
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      apiRequest("/api/auth/login", { method: "POST" }, "public"),
    ).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
