import {
  healthStatuses,
  lifecycleStatuses,
  pageSizes,
  resourceSortFields,
  resourceTypes,
  type ResourceListState,
} from "./resource-api";

function includes<T extends readonly string[]>(
  values: T,
  value: string,
): value is T[number] {
  return values.includes(value as T[number]);
}

export function parseResourceListState(
  params: URLSearchParams,
): ResourceListState {
  const type = params.get("type") ?? "";
  const status = params.get("status") ?? "";
  const healthStatus = params.get("health") ?? "";
  const sort = params.get("sort") ?? "";
  const order = params.get("order") === "desc" ? "desc" : "asc";
  const pageValue = Number(params.get("page"));
  const sizeValue = Number(params.get("size"));
  return {
    search: params.get("search") ?? "",
    type: includes(resourceTypes, type) ? type : "",
    status: includes(lifecycleStatuses, status) ? status : "",
    healthStatus: includes(healthStatuses, healthStatus) ? healthStatus : "",
    sort: includes(resourceSortFields, sort) ? sort : "",
    order,
    page: Number.isInteger(pageValue) && pageValue > 0 ? pageValue - 1 : 0,
    size: includes(pageSizes.map(String), String(sizeValue))
      ? (sizeValue as ResourceListState["size"])
      : 20,
  };
}
