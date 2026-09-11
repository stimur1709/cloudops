import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { OrganizationResponse } from "../api/generated/model";
import { organizationKeys } from "../features/organization/organization-api";
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

const alpha = { id: 11, name: "Alpha Platform" };
const beta = { id: 22, name: "Beta Platform" };

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function authenticatedApi({
  organizations = [alpha, beta],
  roles = { 11: "OWNER", 22: "MEMBER" },
}: {
  organizations?: OrganizationResponse[];
  roles?: Record<number, "OWNER" | "ADMIN" | "MEMBER">;
} = {}) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith("/api/auth/refresh")) return jsonResponse(session);
    if (url.endsWith("/api/auth/me")) return jsonResponse(currentUser);
    if (url.endsWith("/api/organizations/search"))
      return jsonResponse({ items: organizations });
    const membershipMatch = url.match(
      /\/api\/organizations\/(\d+)\/members\/search$/,
    );
    if (membershipMatch) {
      const organizationId = Number(membershipMatch[1]);
      const role = roles[organizationId];
      return jsonResponse({
        items: role ? [{ organizationId, userId: currentUser.id, role }] : [],
      });
    }
    if (url.endsWith("/api/auth/logout"))
      return new Response(null, { status: 204 });
    throw new Error(`Unexpected request: ${url}`);
  });
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

describe("organization-scoped application routing", () => {
  it("redirects an authenticated root to the first available organization", async () => {
    window.history.replaceState({}, "", "/");
    vi.stubGlobal("fetch", authenticatedApi());
    render(<App />);

    expect(screen.getByLabelText("Восстановление сессии")).toBeInTheDocument();
    expect(
      await screen.findByRole("heading", { name: "Ресурсы" }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe("/organizations/11/resources");
    expect(
      screen.getByRole("button", {
        name: "Текущая организация: Alpha Platform. Переключить организацию",
      }),
    ).toHaveTextContent("OWNER");
  });

  it("keeps the organization context when a scoped URL is reloaded", async () => {
    window.history.replaceState({}, "", "/organizations/22/monitoring");
    vi.stubGlobal("fetch", authenticatedApi());
    render(<App />);

    expect(
      await screen.findByRole("heading", { name: "Мониторинг" }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe("/organizations/22/monitoring");
    expect(
      screen.getByRole("button", {
        name: "Текущая организация: Beta Platform. Переключить организацию",
      }),
    ).toHaveTextContent("MEMBER");
  });

  it("switches organization while preserving the logical section and reloads the role", async () => {
    window.history.replaceState({}, "", "/organizations/11/operations");
    const fetchMock = authenticatedApi();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "Операции" });
    await user.click(
      screen.getByRole("button", {
        name: "Текущая организация: Alpha Platform. Переключить организацию",
      }),
    );
    await user.click(
      await screen.findByRole("menuitem", { name: "Beta Platform" }),
    );

    await waitFor(() =>
      expect(window.location.pathname).toBe("/organizations/22/operations"),
    );
    expect(
      await screen.findByRole("button", {
        name: "Текущая организация: Beta Platform. Переключить организацию",
      }),
    ).toHaveTextContent("MEMBER");
    expect(
      fetchMock.mock.calls.some(([url]) =>
        String(url).endsWith("/api/organizations/22/members/search"),
      ),
    ).toBe(true);
  });

  it("does not open scoped content for an unavailable organization", async () => {
    window.history.replaceState({}, "", "/organizations/999/resources");
    vi.stubGlobal("fetch", authenticatedApi());
    render(<App />);

    expect(
      await screen.findByRole("heading", { name: "Организация недоступна" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Ресурсы" })).toBeNull();
    expect(
      screen.getByRole("button", { name: "Alpha Platform" }),
    ).toBeVisible();
  });

  it("shows the first-use state when the user has no organizations", async () => {
    window.history.replaceState({}, "", "/");
    vi.stubGlobal("fetch", authenticatedApi({ organizations: [] }));
    render(<App />);

    expect(
      await screen.findByRole("heading", {
        name: "Создайте первую организацию",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Основная навигация")).toBeNull();
  });

  it("creates the first organization and enters its OWNER context", async () => {
    window.history.replaceState({}, "", "/");
    let organizations: OrganizationResponse[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/refresh")) return jsonResponse(session);
      if (url.endsWith("/api/auth/me")) return jsonResponse(currentUser);
      if (url.endsWith("/api/organizations/search"))
        return jsonResponse({ items: organizations });
      if (url.endsWith("/api/organizations")) {
        organizations = [{ id: 33, name: "First Workspace" }];
        return jsonResponse(organizations[0], 201);
      }
      if (url.endsWith("/api/organizations/33/members/search"))
        return jsonResponse({
          items: [{ organizationId: 33, userId: 1, role: "OWNER" }],
        });
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "Создайте первую организацию" });
    await user.type(
      screen.getByLabelText("Название организации"),
      "First Workspace",
    );
    await user.click(
      screen.getByRole("button", { name: "Создать организацию" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Ресурсы" }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe("/organizations/33/resources");
    expect(
      screen.getByRole("button", {
        name: "Текущая организация: First Workspace. Переключить организацию",
      }),
    ).toHaveTextContent("OWNER");
  });

  it.each(["OWNER", "ADMIN", "MEMBER"] as const)(
    "exposes the %s membership role returned by the backend",
    async (role) => {
      window.history.replaceState({}, "", "/organizations/11/settings");
      vi.stubGlobal(
        "fetch",
        authenticatedApi({ organizations: [alpha], roles: { 11: role } }),
      );
      render(<App />);

      expect(
        await screen.findByRole("button", {
          name: "Текущая организация: Alpha Platform. Переключить организацию",
        }),
      ).toHaveTextContent(role);
    },
  );

  it("uses distinct query keys for organization-scoped data", () => {
    expect(organizationKeys.scoped(11)).not.toEqual(
      organizationKeys.scoped(22),
    );
    expect(organizationKeys.membership(11, 1)).not.toEqual(
      organizationKeys.membership(22, 1),
    );
  });
});

describe("authentication with organization routing", () => {
  it("redirects an unauthenticated protected route to login", async () => {
    window.history.replaceState({}, "", "/organizations/11/monitoring");
    mockMissingSession();
    render(<App />);

    expect(
      await screen.findByRole("heading", { name: "Вход в консоль" }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe("/login");
  });

  it("loads organizations after login without persisting tokens", async () => {
    window.history.replaceState({}, "", "/login");
    const storageSpy = vi.spyOn(Storage.prototype, "setItem");
    let authenticated = false;
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/refresh") && !authenticated)
        return jsonResponse(
          { code: "INVALID_REFRESH_TOKEN", message: "No session" },
          401,
        );
      if (url.endsWith("/api/auth/login")) {
        authenticated = true;
        return jsonResponse(session);
      }
      if (url.endsWith("/api/auth/me")) return jsonResponse(currentUser);
      if (url.endsWith("/api/organizations/search"))
        return jsonResponse({ items: [alpha] });
      if (url.endsWith("/api/organizations/11/members/search"))
        return jsonResponse({
          items: [{ organizationId: 11, userId: 1, role: "ADMIN" }],
        });
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "Вход в консоль" });
    await user.type(screen.getByLabelText("Email"), "operator@example.com");
    await user.type(screen.getByLabelText("Пароль"), "correct password");
    await user.click(screen.getByRole("button", { name: "Войти" }));

    expect(
      await screen.findByRole("heading", { name: "Ресурсы" }),
    ).toBeInTheDocument();
    expect(window.location.pathname).toBe("/organizations/11/resources");
    expect(storageSpy).not.toHaveBeenCalled();
    storageSpy.mockRestore();
  });

  it("continues to log out through the backend", async () => {
    window.history.replaceState({}, "", "/organizations/11/resources");
    let refreshCount = 0;
    const baseApi = authenticatedApi({ organizations: [alpha] });
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith("/api/auth/refresh")) {
        refreshCount += 1;
        if (refreshCount > 1)
          return jsonResponse(
            { code: "INVALID_REFRESH_TOKEN", message: "No session" },
            401,
          );
      }
      return baseApi(input);
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
});
