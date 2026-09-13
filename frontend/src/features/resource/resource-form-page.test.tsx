import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { get1 } from "../../api/generated/cloud-ops";
import { OrganizationContext } from "../organization/organization-context";
import { EditResourcePage } from "./resource-form-page";

vi.mock("../../api/generated/cloud-ops", () => ({
  create: vi.fn(),
  delete2: vi.fn(),
  get1: vi.fn(),
  search3: vi.fn(),
  update1: vi.fn(),
}));

function renderEdit(isManager = true) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrganizationContext.Provider
        value={{
          organization: { id: 11, name: "Alpha" },
          organizationId: 11,
          currentRole: isManager ? "ADMIN" : "MEMBER",
          isManager,
        }}
      >
        <MemoryRouter initialEntries={["/organizations/11/resources/7/edit"]}>
          <Routes>
            <Route
              path="organizations/:organizationId/resources/:resourceId/edit"
              element={<EditResourcePage />}
            />
          </Routes>
        </MemoryRouter>
      </OrganizationContext.Provider>
    </QueryClientProvider>,
  );
}

beforeEach(() => vi.mocked(get1).mockReset());

describe("EditResourcePage", () => {
  it("does not load or show an edit form to a member", () => {
    renderEdit(false);
    expect(
      screen.getByRole("heading", { name: "Недостаточно прав" }),
    ).toBeVisible();
    expect(vi.mocked(get1)).not.toHaveBeenCalled();
  });

  it("does not open a resource from another organization context", async () => {
    vi.mocked(get1).mockResolvedValue({
      data: {
        id: 7,
        organizationId: 12,
        name: "Foreign",
        type: "SERVER",
        status: "ACTIVE",
        config: { host: "foreign.local" },
      },
      status: 200,
      headers: new Headers(),
    });
    renderEdit();
    expect(
      await screen.findByRole("heading", { name: "Ресурс недоступен" }),
    ).toBeVisible();
    expect(screen.queryByLabelText("Имя")).toBeNull();
  });
});
