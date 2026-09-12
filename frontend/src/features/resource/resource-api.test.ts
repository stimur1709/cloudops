import { describe, expect, it } from "vitest";
import {
  buildResourceSearchRequest,
  resourceKeys,
  type ResourceListState,
} from "./resource-api";

const state: ResourceListState = {
  search: "api",
  type: "SERVICE",
  status: "ACTIVE",
  healthStatus: "DEGRADED",
  sort: "updatedAt",
  order: "desc",
  page: 2,
  size: 50,
};

describe("resource search contract", () => {
  it("always scopes the request and sends URL list state to the backend", () => {
    expect(buildResourceSearchRequest(42, state)).toEqual({
      start: 100,
      size: 50,
      filter: {
        operator: "AND",
        conditions: [
          { field: "organizationId", operation: "EQ", value: "42" },
          { field: "name", operation: "CONTAINS", value: "api" },
          { field: "type", operation: "EQ", value: "SERVICE" },
          { field: "status", operation: "EQ", value: "ACTIVE" },
          { field: "healthStatus", operation: "EQ", value: "DEGRADED" },
        ],
      },
      sort: [{ field: "updatedAt", order: "DESC" }],
      getTotal: true,
    });
  });

  it("isolates cached list data by organization and every server input", () => {
    expect(resourceKeys.list(1, state)).not.toEqual(
      resourceKeys.list(2, state),
    );
    expect(resourceKeys.list(1, state)).not.toEqual(
      resourceKeys.list(1, { ...state, search: "database" }),
    );
    expect(resourceKeys.list(1, state)).not.toEqual(
      resourceKeys.list(1, { ...state, page: 3 }),
    );
  });
});
