import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../../api/client/api-error";
import {
  delete1,
  delete3,
  list2,
  list3,
  put,
  put1,
} from "../../api/generated/cloud-ops";
import { SettingsSection } from "./settings-section";
import { settingsKeys } from "./settings-api";

vi.mock("../../api/generated/cloud-ops", () => ({
  delete1: vi.fn(),
  delete3: vi.fn(),
  list2: vi.fn(),
  list3: vi.fn(),
  put: vi.fn(),
  put1: vi.fn(),
}));

const effective = {
  probeType: "HTTP_CHECK" as const,
  enabled: true,
  intervalSeconds: 60,
  failureThreshold: 2,
  recoveryThreshold: 3,
  storageMode: "HISTORY" as const,
  retentionDays: 30,
  timeoutMs: 3000,
  source: "APPLICATION" as const,
};
const application = {
  probeType: "HTTP_CHECK" as const,
  supported: true,
  source: "APPLICATION" as const,
  effective,
  resourceOverride: false,
};
const organization = {
  ...application,
  probeType: "PORT_CHECK" as const,
  source: "ORGANIZATION" as const,
  effective: {
    ...effective,
    probeType: "PORT_CHECK" as const,
    intervalSeconds: 90,
    source: "ORGANIZATION" as const,
  },
};
const resource = {
  ...application,
  probeType: "PING" as const,
  source: "RESOURCE" as const,
  resourceOverride: true,
  effective: {
    ...effective,
    probeType: "PING" as const,
    intervalSeconds: 120,
    source: "RESOURCE" as const,
  },
};
const unsupported = {
  ...application,
  probeType: "DNS_CHECK" as const,
  supported: false,
  effective: {
    ...effective,
    probeType: "DNS_CHECK" as const,
    enabled: false,
    timeoutMs: undefined,
  },
};

function ok<T>(data: T) {
  return Promise.resolve({
    data,
    status: 200 as const,
    headers: new Headers(),
  });
}
function noContent() {
  return Promise.resolve({
    data: undefined,
    status: 204 as const,
    headers: new Headers(),
  });
}
function renderSection(
  scope: "organization" | "resource",
  isManager = true,
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  return {
    client,
    ...render(
      <QueryClientProvider client={client}>
        <SettingsSection
          scope={scope}
          organizationId={11}
          resourceId={7}
          isManager={isManager}
        />
      </QueryClientProvider>,
    ),
  };
}

beforeEach(() => {
  [delete1, delete3, list2, list3, put, put1].forEach((mock) =>
    vi.mocked(mock).mockReset(),
  );
  vi.mocked(list3).mockImplementation(() => ok([application, organization]));
  vi.mocked(list2).mockImplementation(() =>
    ok([application, organization, resource, unsupported]),
  );
  vi.mocked(put1).mockImplementation(() => ok(organization));
  vi.mocked(put).mockImplementation(() => ok(resource));
  vi.mocked(delete3).mockImplementation(() => noContent());
  vi.mocked(delete1).mockImplementation(() => noContent());
});

describe("Monitoring settings", () => {
  it("shows inherited and organization values; member has read-only access", async () => {
    renderSection("organization", false);
    expect(
      (await screen.findAllByText("Наследуется от приложения")).length,
    ).toBeGreaterThan(0);
    expect(screen.getByText("Переопределение организации")).toBeVisible();
    expect(screen.getByText(/только для просмотра/)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Переопределить" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Изменить" })).toBeNull();
  });

  it("separates unsupported from disabled and shows all resource sources", async () => {
    renderSection("resource");
    expect(
      (await screen.findAllByText("Наследуется от приложения")).length,
    ).toBeGreaterThan(0);
    expect(screen.getByText("Наследуется от организации")).toBeVisible();
    expect(screen.getByText("Переопределение ресурса")).toBeVisible();
    const row = screen.getByText("DNS_CHECK").closest("tr")!;
    expect(row).toHaveTextContent("Не поддерживается");
    expect(row).not.toHaveTextContent("Нет");
    expect(row.querySelector("button")).toBeNull();
  });

  it("saves a complete organization override, then reads application values after reset", async () => {
    const user = userEvent.setup();
    vi.mocked(list3)
      .mockImplementationOnce(() => ok([application]))
      .mockImplementation(() =>
        ok([{ ...application, source: "ORGANIZATION" as const }]),
      );
    renderSection("organization");
    await user.click(
      await screen.findByRole("button", { name: "Переопределить" }),
    );
    await user.clear(screen.getByLabelText("Интервал, сек"));
    await user.type(screen.getByLabelText("Интервал, сек"), "90");
    await user.click(screen.getByRole("button", { name: "Сохранить" }));
    await waitFor(() =>
      expect(put1).toHaveBeenCalledWith(11, "HTTP_CHECK", {
        enabled: true,
        intervalSeconds: 90,
        failureThreshold: 2,
        recoveryThreshold: 3,
        storageMode: "HISTORY",
        retentionDays: 30,
        timeoutMs: 3000,
      }),
    );
    await user.click(
      await screen.findByRole("button", {
        name: "Вернуть значения приложения",
      }),
    );
    await waitFor(() => expect(delete3).toHaveBeenCalledWith(11, "HTTP_CHECK"));
    expect(vi.mocked(list3).mock.calls.length).toBeGreaterThan(1);
  });

  it("resets resource override and refetches inherited values", async () => {
    const user = userEvent.setup();
    vi.mocked(list2)
      .mockImplementationOnce(() => ok([resource]))
      .mockImplementation(() => ok([organization]));
    renderSection("resource");
    await user.click(
      await screen.findByRole("button", { name: "Вернуть наследование" }),
    );
    await waitFor(() => expect(delete1).toHaveBeenCalledWith(7, "PING"));
    expect(await screen.findByText("Наследуется от организации")).toBeVisible();
  });

  it("keeps entered values after mutation error and isolates keys", async () => {
    const user = userEvent.setup();
    vi.mocked(put).mockRejectedValueOnce(
      new ApiClientError("Недопустимый интервал", {
        kind: "validation",
        status: 400,
      }),
    );
    renderSection("resource");
    await user.click(
      (await screen.findAllByRole("button", { name: "Переопределить" }))[0]!,
    );
    await user.clear(screen.getByLabelText("Интервал, сек"));
    await user.type(screen.getByLabelText("Интервал, сек"), "75");
    await user.click(screen.getByRole("button", { name: "Сохранить" }));
    expect(await screen.findByText("Недопустимый интервал")).toBeVisible();
    expect(screen.getByLabelText("Интервал, сек")).toHaveValue(75);
    expect(settingsKeys.organization(11)).not.toEqual(
      settingsKeys.organization(12),
    );
    expect(settingsKeys.resource(11, 7)).not.toEqual(
      settingsKeys.resource(11, 8),
    );
    expect(settingsKeys.resource(11, 7)).not.toEqual(
      settingsKeys.organization(11),
    );
  });
});
