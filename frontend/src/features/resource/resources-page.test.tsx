import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../../api/client/api-error";
import { search3 } from "../../api/generated/cloud-ops";
import type { ResourceResponse } from "../../api/generated/model";
import { OrganizationContext } from "../organization/organization-context";
import { ResourcesPage } from "./resources-page";

vi.mock("../../api/generated/cloud-ops", () => ({ search3: vi.fn() }));

const resources: ResourceResponse[] = [
  {
    id: 7,
    organizationId: 11,
    name: "payments-api",
    type: "SERVICE",
    status: "ACTIVE",
    healthStatus: "DEGRADED",
    updatedAt: "2026-09-12T06:30:00Z",
  },
];

function response(items = resources, total = items.length) {
  return Promise.resolve({
    data: { items, total },
    status: 200 as const,
    headers: new Headers(),
  });
}

function renderPage(route = "/organizations/11/resources") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrganizationContext.Provider
        value={{
          organization: { id: 11, name: "Alpha" },
          organizationId: 11,
          currentRole: "OWNER",
          isManager: true,
        }}
      >
        <MemoryRouter initialEntries={[route]}>
          <ResourcesPage />
        </MemoryRouter>
      </OrganizationContext.Provider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(search3).mockReset();
  vi.mocked(search3).mockImplementation(() => response());
});

describe("ResourcesPage", () => {
  it("shows the initial loading state without rendering stale controls", () => {
    vi.mocked(search3).mockImplementation(() => new Promise(() => undefined));
    renderPage();
    expect(screen.getByLabelText("Загрузка ресурсов")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.queryByLabelText("Поиск по имени")).toBeNull();
  });

  it("restores search, filters, sorting and pagination from the URL", async () => {
    renderPage(
      "/organizations/11/resources?search=payments&type=SERVICE&status=ACTIVE&health=DEGRADED&sort=updatedAt&order=desc&page=3&size=50",
    );

    expect(
      await screen.findByRole("heading", { name: "Ресурсы" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Поиск по имени")).toHaveValue("payments");
    expect(screen.getByLabelText("Тип")).toHaveValue("SERVICE");
    expect(screen.getByLabelText("Lifecycle")).toHaveValue("ACTIVE");
    expect(screen.getByLabelText("Здоровье")).toHaveValue("DEGRADED");
    expect(vi.mocked(search3)).toHaveBeenCalledWith(
      expect.objectContaining({
        start: 100,
        size: 50,
        sort: [{ field: "updatedAt", order: "DESC" }],
        filter: expect.objectContaining({
          conditions: expect.arrayContaining([
            { field: "organizationId", operation: "EQ", value: "11" },
            {
              field: "name",
              operation: "CONTAINS",
              value: "payments",
            },
          ]),
        }),
      }),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
  });

  it("debounces server search and exposes keyboard-operable sorting/actions", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findAllByText("payments-api");

    const search = screen.getByLabelText("Поиск по имени");
    await user.type(search, "edge");
    await waitFor(
      () =>
        expect(vi.mocked(search3)).toHaveBeenLastCalledWith(
          expect.objectContaining({
            filter: expect.objectContaining({
              conditions: expect.arrayContaining([
                { field: "name", operation: "CONTAINS", value: "edge" },
              ]),
            }),
          }),
          expect.anything(),
        ),
      { timeout: 1000 },
    );

    const sortButton = screen.getByRole("button", {
      name: "Сортировать по имени",
    });
    sortButton.focus();
    await user.keyboard("{Enter}");
    await waitFor(() =>
      expect(vi.mocked(search3)).toHaveBeenLastCalledWith(
        expect.objectContaining({
          sort: [{ field: "name", order: "ASC" }],
        }),
        expect.anything(),
      ),
    );
    expect(
      screen.getAllByRole("button", {
        name: "Действия для payments-api",
      }).length,
    ).toBeGreaterThan(0);
  });

  it("renders distinct lifecycle and health semantics in desktop and mobile representations", async () => {
    renderPage();
    await screen.findByRole("table", { name: "Ресурсы организации" });

    expect(screen.getAllByText("DEGRADED").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("Активен").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("SERVICE").length).toBeGreaterThanOrEqual(2);
    expect(
      screen.getAllByRole("link", { name: "payments-api" })[0],
    ).toHaveAttribute("href", "/organizations/11/resources/7");
  });

  it.each([
    { route: "/organizations/11/resources", title: "Ресурсов пока нет" },
    {
      route: "/organizations/11/resources?health=DOWN",
      title: "Ресурсы не найдены",
    },
  ])("shows the $title empty state", async ({ route, title }) => {
    vi.mocked(search3).mockImplementation(() => response([], 0));
    renderPage(route);
    expect(
      await screen.findByRole("heading", { name: title }),
    ).toBeInTheDocument();
  });

  it.each([
    {
      kind: "forbidden" as const,
      status: 403,
      title: "Нет доступа к ресурсам",
    },
    {
      kind: "not-found" as const,
      status: 404,
      title: "Ресурсы недоступны",
    },
  ])(
    "maps $status responses to a controlled state",
    async ({ kind, status, title }) => {
      vi.mocked(search3).mockRejectedValue(
        new ApiClientError("Denied", { kind, status }),
      );
      renderPage();
      expect(
        await screen.findByRole("heading", { name: title }),
      ).toBeInTheDocument();
    },
  );

  it("shows a retryable request error", async () => {
    vi.mocked(search3).mockRejectedValue(
      new ApiClientError("Сервис временно недоступен", {
        kind: "unexpected",
        status: 500,
      }),
    );
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Сервис временно недоступен",
    );
    expect(screen.getByRole("button", { name: "Повторить" })).toBeEnabled();
  });

  it("keeps existing rows visible during a background refetch", async () => {
    const user = userEvent.setup();
    type SearchResult = Awaited<ReturnType<typeof search3>>;
    let resolveNext: ((value: SearchResult) => void) | undefined;
    vi.mocked(search3)
      .mockImplementationOnce(() => response())
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveNext = resolve;
          }),
      );
    renderPage();
    await screen.findAllByText("payments-api");

    await user.selectOptions(screen.getByLabelText("Здоровье"), "UP");
    expect(await screen.findByText("Обновление данных")).toBeInTheDocument();
    expect(screen.getAllByText("payments-api").length).toBeGreaterThan(0);
    resolveNext?.((await response()) as SearchResult);
  });
});
