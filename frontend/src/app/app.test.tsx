import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./app";

const session = {
  accessToken: "access-token",
  tokenType: "Bearer",
  expiresIn: 900,
  accessTokenExpiresAt: "2026-09-10T12:15:00Z",
  refreshTokenExpiresAt: "2026-09-17T12:00:00Z",
};

const currentUser = {
  id: 1,
  email: "operator@example.com",
  displayName: "Cloud Operator",
  createdAt: "2026-09-01T12:00:00Z",
  updatedAt: "2026-09-01T12:00:00Z",
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockAuthenticatedBootstrap() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/refresh")) return jsonResponse(session);
      if (url.endsWith("/api/auth/me")) return jsonResponse(currentUser);
      throw new Error(`Unexpected request: ${url}`);
    }),
  );
}

function mockMissingSession() {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        jsonResponse(
          { code: "INVALID_REFRESH_TOKEN", message: "No session" },
          401,
        ),
      ),
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe("application auth and routing", () => {
  it("shows bootstrap without flashing login, then restores the authenticated App Shell", async () => {
    window.history.replaceState({}, "", "/resources");
    mockAuthenticatedBootstrap();
    render(<App />);

    expect(screen.getByLabelText("Восстановление сессии")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Вход в консоль" }),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByRole("heading", { name: "Ресурсы" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("navigation", { name: "Основная навигация" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ресурсы" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByText("Cloud Operator")).toBeInTheDocument();
  });

  it("redirects an unauthenticated protected route to accessible login", async () => {
    window.history.replaceState({}, "", "/monitoring");
    mockMissingSession();
    render(<App />);

    expect(
      await screen.findByRole("heading", { name: "Вход в консоль" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.getByLabelText("Пароль")).toBeInTheDocument();
    expect(window.location.pathname).toBe("/login");
  });

  it("logs in, loads the current user, and does not persist tokens in browser storage", async () => {
    window.history.replaceState({}, "", "/login");
    const storageSpy = vi.spyOn(Storage.prototype, "setItem");
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/refresh"))
        return jsonResponse(
          { code: "INVALID_REFRESH_TOKEN", message: "No session" },
          401,
        );
      if (url.endsWith("/api/auth/login")) return jsonResponse(session);
      if (url.endsWith("/api/auth/me")) return jsonResponse(currentUser);
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "Вход в консоль" });
    await user.type(screen.getByLabelText("Email"), "operator@example.com");
    await user.type(
      screen.getByLabelText("Пароль"),
      "correct horse battery staple",
    );
    await user.click(screen.getByRole("button", { name: "Войти" }));

    await waitFor(() =>
      expect(fetchMock.mock.calls.map(([url]) => String(url))).toContain(
        "/api/auth/login",
      ),
    );
    expect(
      await screen.findByRole("heading", { name: "Ресурсы" }),
    ).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.filter(([url]) =>
        String(url).endsWith("/api/auth/login"),
      ),
    ).toHaveLength(1);
    expect(storageSpy).not.toHaveBeenCalled();
    storageSpy.mockRestore();
  });

  it("renders a controlled backend login error", async () => {
    window.history.replaceState({}, "", "/login");
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/refresh"))
        return jsonResponse(
          { code: "INVALID_REFRESH_TOKEN", message: "No session" },
          401,
        );
      return jsonResponse(
        { code: "BAD_CREDENTIALS", message: "Bad credentials" },
        401,
      );
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "Вход в консоль" });
    await user.type(screen.getByLabelText("Email"), "operator@example.com");
    await user.type(screen.getByLabelText("Пароль"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Войти" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Неверный email или пароль.",
    );
  });

  it("logs out through the backend and clears the authenticated UI", async () => {
    window.history.replaceState({}, "", "/resources");
    let refreshCount = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/refresh")) {
        refreshCount += 1;
        return refreshCount === 1
          ? jsonResponse(session)
          : jsonResponse(
              { code: "INVALID_REFRESH_TOKEN", message: "No session" },
              401,
            );
      }
      if (url.endsWith("/api/auth/me")) return jsonResponse(currentUser);
      if (url.endsWith("/api/auth/logout"))
        return new Response(null, { status: 204 });
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "Ресурсы" });
    await user.click(
      screen.getByRole("button", { name: "Открыть меню пользователя" }),
    );
    await user.click(await screen.findByRole("menuitem", { name: "Выйти" }));

    expect(
      await screen.findByRole("heading", { name: "Вход в консоль" }),
    ).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).endsWith("/api/auth/logout"),
      ),
    ).toBe(true);
  });

  it("shows the not-found page for an unknown route", async () => {
    window.history.replaceState({}, "", "/missing-page");
    mockAuthenticatedBootstrap();
    render(<App />);
    expect(
      await screen.findByRole("heading", { name: "Страница не найдена" }),
    ).toBeInTheDocument();
    await waitFor(() => expect(window.location.pathname).toBe("/missing-page"));
  });
});
