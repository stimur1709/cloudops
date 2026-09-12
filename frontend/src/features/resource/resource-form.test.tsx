import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../../api/client/api-error";
import {
  buildResourceRequest,
  serializeResourceConfig,
} from "./resource-form-serialization";
import { ResourceForm } from "./resource-form";

describe("ResourceForm", () => {
  it("serializes only the config selected by the current resource type", () => {
    expect(
      serializeResourceConfig({
        name: "server",
        type: "SERVER",
        status: "ACTIVE",
        config: {
          host: "srv.local",
          port: 8080,
          sshPort: 22,
          url: "https://stale.example",
        },
      }),
    ).toEqual({ host: "srv.local", port: 8080, sshPort: 22 });
    expect(
      serializeResourceConfig({
        name: "router",
        type: "NETWORK_DEVICE",
        status: "ACTIVE",
        config: {
          host: "router.local",
          managementPort: 8443,
          database: "stale",
        },
      }),
    ).toEqual({ host: "router.local", managementPort: 8443 });
    expect(
      serializeResourceConfig({
        name: "db",
        type: "DATABASE",
        status: "ACTIVE",
        config: {
          host: "db.local",
          port: 5432,
          database: "cloudops",
          url: "https://stale.example",
        },
      }),
    ).toEqual({ host: "db.local", port: 5432, database: "cloudops" });
    expect(
      serializeResourceConfig({
        name: "service",
        type: "SERVICE",
        status: "ACTIVE",
        config: {
          url: "https://service.example",
          expectedStatus: 204,
          host: "stale",
        },
      }),
    ).toEqual({ url: "https://service.example", expectedStatus: 204 });
    expect(
      buildResourceRequest(
        {
          name: " api ",
          type: "OTHER",
          status: "INACTIVE",
          config: { host: "stale" },
        },
        11,
      ),
    ).toEqual({
      name: "api",
      type: "OTHER",
      status: "INACTIVE",
      organizationId: 11,
      config: {},
    });
  });

  it("switches config shape and always submits the current organization id", async () => {
    const user = userEvent.setup();
    const submit = vi.fn().mockResolvedValue(undefined);
    render(
      <MemoryRouter>
        <ResourceForm organizationId={11} onSubmit={submit} />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("Имя"), "gateway");
    await user.type(screen.getByLabelText("Хост"), "stale.local");
    await user.click(screen.getByRole("combobox", { name: "Тип" }));
    await user.click(screen.getByRole("option", { name: "Сервис" }));
    expect(screen.queryByLabelText("Хост")).toBeNull();
    await user.clear(screen.getByLabelText("URL"));
    await user.type(screen.getByLabelText("URL"), "https://gateway.example");
    await user.click(screen.getByRole("button", { name: "Создать ресурс" }));
    await waitFor(() =>
      expect(submit).toHaveBeenCalledWith({
        name: "gateway",
        type: "SERVICE",
        status: "ACTIVE",
        organizationId: 11,
        config: { url: "https://gateway.example", expectedStatus: 200 },
      }),
    );
  });

  it("maps nested backend errors and preserves input after a failed submit", async () => {
    const user = userEvent.setup();
    const submit = vi.fn().mockRejectedValue(
      new ApiClientError("Validation failed", {
        kind: "validation",
        status: 400,
        details: {
          code: "VALIDATION_ERROR",
          message: "Validation failed",
          timestamp: "2026-09-12T00:00:00Z",
          path: "/api/resources",
          errors: [{ field: "config.host", message: "Host is unavailable" }],
        },
      }),
    );
    render(
      <MemoryRouter>
        <ResourceForm organizationId={11} onSubmit={submit} />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("Имя"), "keep-me");
    await user.type(screen.getByLabelText("Хост"), "bad.local");
    await user.click(screen.getByRole("button", { name: "Создать ресурс" }));
    expect(await screen.findByText("Host is unavailable")).toBeVisible();
    expect(screen.getByLabelText("Имя")).toHaveValue("keep-me");
    expect(screen.getByLabelText("Хост")).toHaveValue("bad.local");
  });

  it("shows a controlled name error for RESOURCE_NAME_CONFLICT", async () => {
    const user = userEvent.setup();
    const submit = vi.fn().mockRejectedValue(
      new ApiClientError("Conflict", {
        kind: "conflict",
        status: 409,
        details: {
          code: "RESOURCE_NAME_CONFLICT",
          message: "Conflict",
          timestamp: "2026-09-12T00:00:00Z",
          path: "/api/resources",
          errors: [],
        },
      }),
    );
    render(
      <MemoryRouter>
        <ResourceForm organizationId={11} onSubmit={submit} />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText("Имя"), "duplicate");
    await user.type(screen.getByLabelText("Хост"), "host.local");
    await user.click(screen.getByRole("button", { name: "Создать ресурс" }));
    expect(
      await screen.findByText(
        "Ресурс с таким именем уже существует в организации.",
      ),
    ).toBeVisible();
  });
});
