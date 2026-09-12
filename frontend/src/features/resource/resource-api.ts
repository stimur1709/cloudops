import { search3 } from "../../api/generated/cloud-ops";
import type {
  Condition,
  ResourceResponse,
  SearchRequest,
} from "../../api/generated/model";

export const resourceTypes = [
  "NETWORK_DEVICE",
  "SERVER",
  "DATABASE",
  "SERVICE",
  "OTHER",
] as const;
export const lifecycleStatuses = ["ACTIVE", "INACTIVE"] as const;
export const healthStatuses = ["UP", "DEGRADED", "DOWN", "UNKNOWN"] as const;
export const resourceSortFields = [
  "name",
  "type",
  "status",
  "updatedAt",
] as const;
export const pageSizes = [20, 50, 100] as const;

export type ResourceTypeFilter = (typeof resourceTypes)[number] | "";
export type LifecycleFilter = (typeof lifecycleStatuses)[number] | "";
export type HealthFilter = (typeof healthStatuses)[number] | "";
export type ResourceSortField = (typeof resourceSortFields)[number] | "";

export interface ResourceListState {
  search: string;
  type: ResourceTypeFilter;
  status: LifecycleFilter;
  healthStatus: HealthFilter;
  sort: ResourceSortField;
  order: "asc" | "desc";
  page: number;
  size: (typeof pageSizes)[number];
}

export interface ResourcePage {
  items: ResourceResponse[];
  total?: number;
}

export const resourceKeys = {
  list: (organizationId: number, state: ResourceListState) =>
    [
      "resources",
      organizationId,
      state.page,
      state.size,
      state.sort,
      state.order,
      state.type,
      state.status,
      state.healthStatus,
      state.search,
    ] as const,
};

export function buildResourceSearchRequest(
  organizationId: number,
  state: ResourceListState,
): SearchRequest {
  const conditions: Condition[] = [
    { field: "organizationId", operation: "EQ", value: String(organizationId) },
  ];
  const search = state.search.trim();
  if (search)
    conditions.push({ field: "name", operation: "CONTAINS", value: search });
  if (state.type)
    conditions.push({ field: "type", operation: "EQ", value: state.type });
  if (state.status)
    conditions.push({ field: "status", operation: "EQ", value: state.status });
  if (state.healthStatus)
    conditions.push({
      field: "healthStatus",
      operation: "EQ",
      value: state.healthStatus,
    });

  return {
    start: state.page * state.size,
    size: state.size,
    filter: { operator: "AND", conditions },
    sort: state.sort
      ? [
          {
            field: state.sort,
            order: state.order.toUpperCase() as "ASC" | "DESC",
          },
        ]
      : undefined,
    getTotal: true,
  };
}

export async function getResources(
  organizationId: number,
  state: ResourceListState,
  signal?: AbortSignal,
): Promise<ResourcePage> {
  const response = await search3(
    buildResourceSearchRequest(organizationId, state),
    {
      signal,
    },
  );
  return response.data as ResourcePage;
}
