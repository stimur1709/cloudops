import { list1, run1, searchResults } from "../../api/generated/cloud-ops";
import type {
  MonitorResponse,
  MonitoringResultResponse,
  SearchRequest,
} from "../../api/generated/model";

export const monitoringKeys = {
  all: ["monitoring"] as const,
  resource: (organizationId: number, resourceId: number) =>
    ["monitoring", organizationId, "resource", resourceId] as const,
  history: (
    organizationId: number,
    resourceId: number,
    monitorId: number,
    page: number,
    size: number,
    order: "asc" | "desc",
  ) =>
    [
      "monitoring",
      organizationId,
      "resource",
      resourceId,
      "monitor",
      monitorId,
      "history",
      page,
      size,
      order,
    ] as const,
  historyScope: (
    organizationId: number,
    resourceId: number,
    monitorId: number,
  ) =>
    [
      "monitoring",
      organizationId,
      "resource",
      resourceId,
      "monitor",
      monitorId,
      "history",
    ] as const,
};

export interface MonitoringResultsPage {
  items: MonitoringResultResponse[];
  total?: number;
}

export async function getResourceMonitors(
  resourceId: number,
  signal?: AbortSignal,
): Promise<MonitorResponse[]> {
  const response = await list1(resourceId, { signal });
  return response.data as MonitorResponse[];
}

export async function requestMonitorRun(monitorId: number): Promise<void> {
  await run1(monitorId);
}

export async function getMonitorHistory(
  monitorId: number,
  page: number,
  size: number,
  order: "asc" | "desc",
  signal?: AbortSignal,
): Promise<MonitoringResultsPage> {
  const request: SearchRequest = {
    start: page * size,
    size,
    sort: [
      { field: "checkedAt", order: order.toUpperCase() as "ASC" | "DESC" },
    ],
    getTotal: true,
  };
  const response = await searchResults(monitorId, request, { signal });
  return response.data as MonitoringResultsPage;
}
