import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../../api/client/api-error";
import { list1, run1, searchResults } from "../../api/generated/cloud-ops";
import { MonitoringSection } from "./monitoring-section";
import { formatDateTime } from "../resource/resource-details-format";

vi.mock("../../api/generated/cloud-ops", () => ({
  list1: vi.fn(),
  run1: vi.fn(),
  searchResults: vi.fn(),
}));

const monitor = {
  id: 21,
  resourceId: 7,
  type: "HTTP_CHECK" as const,
  healthStatus: "DOWN" as const,
  lastCheckedAt: "2026-09-14T10:00:00Z",
  nextRunAt: "2026-09-14T10:05:00Z",
  lastResult: {
    success: false,
    data: {
      url: "https://payments.example.test",
      statusCode: 503,
      expectedStatus: 204,
      responseTimeMs: 42,
      matchedExpectedStatus: false,
    },
  },
};

function ok<T, S extends 200 | 202 = 200>(data: T, status: S = 200 as S) {
  return Promise.resolve({ data, status, headers: new Headers() });
}

function renderSection() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MonitoringSection organizationId={11} resourceId={7} enabled />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(list1).mockReset();
  vi.mocked(run1).mockReset();
  vi.mocked(searchResults).mockReset();
  vi.mocked(list1).mockImplementation(() => ok([monitor]));
  vi.mocked(run1).mockImplementation(() => ok(undefined, 202));
  vi.mocked(searchResults).mockImplementation(() =>
    ok({
      items: [
        {
          id: 31,
          monitorId: 21,
          checkedAt: "2026-09-14T10:00:00Z",
          result: monitor.lastResult,
        },
      ],
      total: 1,
    }),
  );
});

describe("MonitoringSection", () => {
  it("loads monitor health, typed last result and server-side history", async () => {
    const user = userEvent.setup();
    renderSection();

    expect(await screen.findByText("HTTP")).toBeVisible();
    expect(screen.getByText("DOWN")).toBeVisible();
    expect(screen.getByText("Неуспешно")).toBeVisible();
    await user.click(screen.getByText("Технические детали"));
    expect(screen.getByText("HTTP_CHECK")).toBeVisible();
    expect(screen.getByText("503")).toBeVisible();
    expect(screen.queryByText("История HTTP")).toBeNull();

    const historyButton = screen.getByRole("button", {
      name: "Показать историю HTTP",
    });
    await user.click(historyButton);
    expect(historyButton.closest("tr")).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(await screen.findByText("История HTTP")).toBeVisible();
    expect(searchResults).toHaveBeenCalledWith(
      21,
      {
        start: 0,
        size: 10,
        sort: [{ field: "checkedAt", order: "DESC" }],
        getTotal: true,
      },
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
  });

  it("shows never-run and unscheduled states without treating them as failures", async () => {
    vi.mocked(list1).mockImplementation(() =>
      ok([
        {
          ...monitor,
          healthStatus: "UNKNOWN" as const,
          lastCheckedAt: null,
          lastResult: null,
          nextRunAt: null,
        },
      ]),
    );
    vi.mocked(searchResults).mockImplementation(() =>
      ok({ items: [], total: 0 }),
    );
    renderSection();

    expect(
      await screen.findByText("Проверка ещё не выполнялась"),
    ).toBeInTheDocument();
    expect(screen.getByText("Не выполнялась")).toBeInTheDocument();
    expect(screen.getByText("Не запланирован")).toBeInTheDocument();
    expect(screen.getByText("UNKNOWN")).toBeVisible();
  });

  it("requests a manual run, keeps the periodic schedule and refetches", async () => {
    const user = userEvent.setup();
    renderSection();
    await user.click(
      await screen.findByRole("button", { name: "Запустить сейчас" }),
    );

    await waitFor(() => expect(run1).toHaveBeenCalledWith(21));
    expect(
      await screen.findByRole("button", { name: "Запуск запрошен…" }),
    ).toBeDisabled();
    await waitFor(() => expect(list1).toHaveBeenCalledTimes(2));
    expect(
      screen.getByTitle(formatDateTime(monitor.nextRunAt)),
    ).toHaveAttribute("datetime", monitor.nextRunAt);
  });

  it("does not block manual runs for other monitors", async () => {
    const user = userEvent.setup();
    vi.mocked(list1).mockImplementation(() =>
      ok([
        monitor,
        {
          ...monitor,
          id: 22,
          type: "DNS_CHECK" as const,
          healthStatus: "UP" as const,
        },
      ]),
    );
    vi.mocked(run1).mockImplementationOnce(() => new Promise<never>(() => {}));
    renderSection();

    const runButtons = await screen.findAllByRole("button", {
      name: "Запустить сейчас",
    });
    await user.click(runButtons[0]!);
    await waitFor(() => expect(run1).toHaveBeenCalledWith(21));
    expect(runButtons[1]).toBeEnabled();
  });

  it("keeps controlled run and history conflicts local to their monitor", async () => {
    const user = userEvent.setup();
    vi.mocked(run1).mockRejectedValue(
      new ApiClientError("Disabled", {
        kind: "conflict",
        status: 409,
        details: { code: "MONITOR_DISABLED", message: "Disabled" } as never,
      }),
    );
    vi.mocked(searchResults).mockRejectedValue(
      new ApiClientError("History disabled", {
        kind: "conflict",
        status: 409,
        details: {
          code: "MONITOR_HISTORY_NOT_ENABLED",
          message: "History disabled",
        } as never,
      }),
    );
    renderSection();

    await user.click(
      await screen.findByRole("button", { name: "Показать историю HTTP" }),
    );
    expect(await screen.findByText("История не включена")).toBeVisible();
    expect(
      screen.queryByRole("combobox", { name: "Сортировка истории" }),
    ).toBeNull();
    expect(screen.getByText("HTTP")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Запустить сейчас" }));
    expect(
      await screen.findByText(
        "Монитор отключён. Измените настройки мониторинга перед запуском.",
      ),
    ).toBeVisible();
    expect(screen.getByText("DOWN")).toBeVisible();
  });

  it("shows incompatible conflicts without applying frontend compatibility rules", async () => {
    const user = userEvent.setup();
    vi.mocked(run1).mockRejectedValue(
      new ApiClientError("Incompatible", {
        kind: "conflict",
        status: 409,
        details: {
          code: "MONITOR_INCOMPATIBLE",
          message: "Incompatible",
        } as never,
      }),
    );
    renderSection();
    await user.click(
      await screen.findByRole("button", { name: "Запустить сейчас" }),
    );
    expect(
      await screen.findByText(
        "Монитор несовместим с текущей конфигурацией ресурса.",
      ),
    ).toBeVisible();
  });
});
