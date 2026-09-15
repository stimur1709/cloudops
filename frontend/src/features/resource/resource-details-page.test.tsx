import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../../api/client/api-error";
import {
  delete2,
  get1,
  get5,
  list1,
  run1,
  search2,
  searchResults,
} from "../../api/generated/cloud-ops";
import { OrganizationContext } from "../organization/organization-context";
import { ResourceDetailsPage } from "./resource-details-page";

vi.mock("../../api/generated/cloud-ops", () => ({
  delete2: vi.fn(),
  get1: vi.fn(),
  get5: vi.fn(),
  list1: vi.fn(),
  run1: vi.fn(),
  search2: vi.fn(),
  searchResults: vi.fn(),
}));

const resource = {
  id: 7,
  organizationId: 11,
  name: "payments-api",
  type: "SERVICE" as const,
  status: "ACTIVE" as const,
  healthStatus: "DEGRADED" as const,
  config: { url: "https://payments.example.test", expectedStatus: 204 },
  createdAt: "2026-09-01T10:00:00Z",
  updatedAt: "2026-09-12T06:30:00Z",
};

function ok<T>(data: T) {
  return Promise.resolve({
    data,
    status: 200 as const,
    headers: new Headers(),
  });
}

function renderPage({
  route = "/organizations/11/resources/7",
  isManager = true,
  entryState,
}: {
  route?: string;
  isManager?: boolean;
  entryState?: Record<string, unknown>;
} = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrganizationContext.Provider
        value={{
          organization: { id: 11, name: "Alpha" },
          organizationId: 11,
          currentRole: isManager ? "OWNER" : "MEMBER",
          isManager,
        }}
      >
        <MemoryRouter
          initialEntries={[
            {
              pathname: route.split("?")[0],
              search: route.includes("?") ? `?${route.split("?")[1]}` : "",
              state: entryState,
            },
          ]}
        >
          <Routes>
            <Route
              path="/organizations/:organizationId/resources/:resourceId"
              element={<ResourceDetailsPage />}
            />
          </Routes>
        </MemoryRouter>
      </OrganizationContext.Provider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(get1).mockReset();
  vi.mocked(get5).mockReset();
  vi.mocked(search2).mockReset();
  vi.mocked(list1).mockReset();
  vi.mocked(run1).mockReset();
  vi.mocked(searchResults).mockReset();
  vi.mocked(delete2).mockReset();
  vi.mocked(get1).mockImplementation(() => ok(resource));
  vi.mocked(get5).mockImplementation(() =>
    ok({
      from: "2026-09-12T12:00:00Z",
      to: "2026-09-13T12:00:00Z",
      periodSeconds: 86400,
      upSeconds: 40000,
      degradedSeconds: 10000,
      downSeconds: 5000,
      unknownSeconds: 31400,
      knownSeconds: 55000,
      uptimePercent: undefined,
      availabilityPercent: null as unknown as number,
      coveragePercent: 63.66,
    }),
  );
  vi.mocked(list1).mockImplementation(() => ok([]));
  vi.mocked(searchResults).mockImplementation(() =>
    ok({ items: [], total: 0 }),
  );
  vi.mocked(search2).mockImplementation(() =>
    ok({
      items: [
        {
          id: 3,
          fromStatus: "UP" as const,
          toStatus: "DEGRADED" as const,
          changedAt: "2026-09-13T11:30:00Z",
        },
      ],
      total: 1,
    }),
  );
});

describe("ResourceDetailsPage", () => {
  it("does not expose a resource from another organization or request its health", async () => {
    vi.mocked(get1).mockImplementation(() =>
      ok({ ...resource, organizationId: 22 }),
    );
    renderPage();
    expect(
      await screen.findByRole("heading", { name: "Ресурс недоступен" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("payments-api")).toBeNull();
    expect(get5).not.toHaveBeenCalled();
    expect(search2).not.toHaveBeenCalled();
    expect(list1).not.toHaveBeenCalled();
  });

  it("keeps lifecycle, health and server availability semantics distinct", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(
      await screen.findByRole("heading", { name: "payments-api" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("DEGRADED").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Активен").length).toBeGreaterThan(0);
    expect(screen.getByText("https://payments.example.test")).toBeVisible();
    expect(get5).not.toHaveBeenCalled();

    await user.click(screen.getByRole("tab", { name: "Здоровье" }));
    expect(await screen.findByText("63,66%")).toBeVisible();
    expect(screen.getByText("Покрытие")).toBeVisible();
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText("0%")).toBeNull();
    expect(screen.queryByText("https://payments.example.test")).toBeNull();
  });

  it("keeps resource and monitor health distinct and allows members to run monitors", async () => {
    vi.mocked(list1).mockImplementation(() =>
      ok([
        {
          id: 21,
          resourceId: 7,
          type: "HTTP_CHECK" as const,
          healthStatus: "DOWN" as const,
          lastCheckedAt: null,
          lastResult: null,
          nextRunAt: null,
        },
      ]),
    );
    renderPage({ isManager: false });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("tab", { name: "Мониторинг" }));

    expect(await screen.findByText("HTTP")).toBeVisible();
    expect(screen.getAllByText("DEGRADED").length).toBeGreaterThan(0);
    expect(screen.getAllByText("DOWN").length).toBeGreaterThan(0);
    expect(
      screen.getByRole("button", { name: "Запустить сейчас" }),
    ).toBeVisible();
  });

  it("uses the selected period as exact from/to request parameters", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("tab", { name: "Здоровье" }));
    await screen.findByText("63,66%");
    await user.click(
      screen.getByRole("combobox", { name: "Период доступности" }),
    );
    await user.click(screen.getByRole("option", { name: "7 дней" }));
    await waitFor(() => expect(get5).toHaveBeenCalledTimes(2));
    const [, params] = vi.mocked(get5).mock.calls.at(-1)!;
    expect(Date.parse(params.to) - Date.parse(params.from)).toBe(
      7 * 24 * 60 * 60 * 1000,
    );
  });

  it("shows real newest-first transitions", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("tab", { name: "Здоровье" }));
    expect((await screen.findAllByText("UP")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("DEGRADED").length).toBeGreaterThan(0);
    expect(search2).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        start: 0,
        size: 10,
        sort: [{ field: "changedAt", order: "DESC" }],
      }),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
  });

  it("loads monitoring only after the resource is confirmed in the organization", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(list1).not.toHaveBeenCalled();
    await user.click(await screen.findByRole("tab", { name: "Мониторинг" }));
    expect(
      await screen.findByRole("heading", { name: "Мониторинг" }),
    ).toBeVisible();
    expect(list1).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
    expect(screen.getByRole("tab", { name: "Мониторинг" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.queryByText("Конфигурация")).toBeNull();
  });

  it("keeps Overview visible when secondary health requests fail", async () => {
    const user = userEvent.setup();
    vi.mocked(get5).mockRejectedValue(
      new ApiClientError("Failed", { kind: "network" }),
    );
    vi.mocked(search2).mockRejectedValue(
      new ApiClientError("Failed", { kind: "network" }),
    );
    renderPage();
    expect(
      await screen.findByText("https://payments.example.test"),
    ).toBeVisible();
    await user.click(screen.getByRole("tab", { name: "Здоровье" }));
    expect(
      await screen.findByText(
        "Не удалось загрузить доступность за выбранный период.",
      ),
    ).toBeVisible();
    expect(
      await screen.findByText(
        "Не удалось загрузить историю изменений здоровья.",
      ),
    ).toBeVisible();
    expect(screen.getByRole("heading", { name: "payments-api" })).toBeVisible();
  });

  it("shows mutations only to managers and restores the list URL", async () => {
    const member = renderPage({
      isManager: false,
      entryState: { resourceListSearch: "search=payments&page=3" },
    });
    await screen.findByRole("heading", { name: "payments-api" });
    expect(screen.queryByRole("link", { name: "Редактировать" })).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Действия с ресурсом" }),
    ).toBeNull();
    expect(
      screen.getByRole("link", { name: "К списку ресурсов" }),
    ).toHaveAttribute(
      "href",
      "/organizations/11/resources?search=payments&page=3",
    );
    member.unmount();

    const managerUser = userEvent.setup();
    renderPage();
    expect(
      await screen.findByRole("link", { name: "Редактировать" }),
    ).toBeVisible();
    expect(screen.queryByRole("button", { name: "Удалить ресурс" })).toBeNull();
    await managerUser.click(
      screen.getByRole("button", { name: "Действия с ресурсом" }),
    );
    expect(
      await screen.findByRole("menuitem", { name: "Удалить ресурс" }),
    ).toBeVisible();
  });
});
