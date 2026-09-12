import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../../api/client/api-error";
import { delete2, search3 } from "../../api/generated/cloud-ops";
import type { ResourceResponse } from "../../api/generated/model";
import { OrganizationContext } from "../organization/organization-context";
import { ResourcesPage } from "./resources-page";

vi.mock("../../api/generated/cloud-ops", () => ({
  delete2: vi.fn(),
  search3: vi.fn(),
}));

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

function renderPage(route = "/organizations/11/resources", isManager = true) {
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
        <MemoryRouter initialEntries={[route]}>
          <ResourcesPage />
        </MemoryRouter>
      </OrganizationContext.Provider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(search3).mockReset();
  vi.mocked(delete2).mockReset();
  vi.mocked(search3).mockImplementation(() => response());
  vi.mocked(delete2).mockResolvedValue({
    data: undefined,
    status: 204,
    headers: new Headers(),
  });
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
    expect(screen.getByRole("combobox", { name: "Тип" })).toHaveTextContent(
      "Сервис",
    );
    expect(screen.getByRole("combobox", { name: "Статус" })).toHaveTextContent(
      "Активен",
    );
    expect(
      screen.getByRole("combobox", { name: "Здоровье" }),
    ).toHaveTextContent("DEGRADED");
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
    expect(sortButton.closest("th")).not.toHaveAttribute("aria-sort");
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
    expect(sortButton.closest("th")).toHaveAttribute("aria-sort", "ascending");
    expect(
      screen.getByRole("button", { name: "Сортировать по типу" }).closest("th"),
    ).not.toHaveAttribute("aria-sort");
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
    expect(screen.getAllByText("Сервис").length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText("SERVICE")).toBeNull();
    expect(screen.queryByText("ACTIVE")).toBeNull();
    expect(
      screen.getAllByRole("link", { name: "payments-api" })[0],
    ).toHaveAttribute("href", "/organizations/11/resources/7");
  });

  it.each([
    { route: "/organizations/11/resources", title: "Ресурсов ещё нет" },
    {
      route: "/organizations/11/resources?health=DOWN",
      title: "Ничего не найдено",
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

    screen.getByRole("combobox", { name: "Здоровье" }).focus();
    await user.keyboard("{Enter}{ArrowDown}{Enter}");
    expect(await screen.findByText("Обновление данных")).toBeInTheDocument();
    expect(screen.getAllByText("payments-api").length).toBeGreaterThan(0);
    resolveNext?.((await response()) as SearchResult);
  });

  it("uses localized Select labels while preserving raw API enum values", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findAllByText("payments-api");

    const typeSelect = screen.getByRole("combobox", { name: "Тип" });
    expect(typeSelect).toHaveTextContent("Все типы");
    expect(
      screen.queryByRole("button", { name: "Сбросить фильтры" }),
    ).toBeNull();

    await user.click(typeSelect);
    const allTypesOption = await screen.findByRole("option", {
      name: "Все типы",
    });
    expect(allTypesOption).toHaveAttribute("data-state", "checked");
    expect(allTypesOption.querySelector("svg")).not.toBeNull();
    const serverOption = await screen.findByRole("option", { name: "Сервер" });
    await user.click(serverOption);

    await waitFor(() =>
      expect(vi.mocked(search3)).toHaveBeenLastCalledWith(
        expect.objectContaining({
          filter: expect.objectContaining({
            conditions: expect.arrayContaining([
              { field: "type", operation: "EQ", value: "SERVER" },
            ]),
          }),
        }),
        expect.anything(),
      ),
    );
    expect(screen.getByRole("combobox", { name: "Тип" })).toHaveTextContent(
      "Сервер",
    );
    expect(
      screen.getByRole("button", { name: "Сбросить фильтры" }),
    ).toBeVisible();
  });

  it("shows a result range and uses the shared Select for page size", async () => {
    const pageResources = Array.from({ length: 20 }, (_, index) => ({
      ...resources[0],
      id: index + 21,
      name: `resource-${index + 21}`,
    }));
    vi.mocked(search3).mockImplementation(() => response(pageResources, 73));
    renderPage("/organizations/11/resources?page=2");
    await screen.findAllByText("resource-21");

    expect(screen.getByText("21–40 из 73")).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: "Строк на странице" }),
    ).toHaveTextContent("20");
  });

  it("shows mutation actions only to organization managers", async () => {
    const user = userEvent.setup();
    const memberView = renderPage(undefined, false);
    await screen.findAllByText("payments-api");
    expect(screen.queryByRole("link", { name: "Добавить ресурс" })).toBeNull();
    await user.click(
      screen.getAllByRole("button", { name: "Действия для payments-api" })[0]!,
    );
    expect(
      screen.queryByRole("menuitem", { name: "Редактировать" }),
    ).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Удалить" })).toBeNull();
    memberView.unmount();

    renderPage();
    await screen.findAllByText("payments-api");
    expect(screen.getByRole("link", { name: "Добавить ресурс" })).toBeVisible();
    await user.click(
      screen.getAllByRole("button", { name: "Действия для payments-api" })[0]!,
    );
    expect(
      screen.getByRole("menuitem", { name: "Редактировать" }),
    ).toBeVisible();
    expect(screen.getByRole("menuitem", { name: "Удалить" })).toBeVisible();
  });

  it("requires named confirmation before deleting and refreshes the list", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findAllByText("payments-api");
    await user.click(
      screen.getAllByRole("button", { name: "Действия для payments-api" })[0]!,
    );
    await user.click(screen.getByRole("menuitem", { name: "Удалить" }));
    expect(screen.getByRole("alertdialog")).toHaveTextContent(
      "Удалить ресурс payments-api?",
    );
    expect(vi.mocked(delete2)).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Удалить ресурс" }));
    await waitFor(() => expect(vi.mocked(delete2)).toHaveBeenCalledWith(7));
    await waitFor(() =>
      expect(vi.mocked(search3).mock.calls.length).toBeGreaterThan(1),
    );
  });
});
