import {
  delete1,
  delete3,
  list2,
  list3,
  put,
  put1,
} from "../../api/generated/cloud-ops";
import type {
  ProbeSettingsRequest,
  ProbeSettingsResponse,
  ProbeSettingsResponseProbeType,
} from "../../api/generated/model";

export type ProbeType = ProbeSettingsResponseProbeType;
export type SettingsScope = "organization" | "resource";

export const settingsKeys = {
  organization: (organizationId: number) =>
    ["monitoring-settings", organizationId, "organization"] as const,
  resource: (organizationId: number, resourceId: number) =>
    ["monitoring-settings", organizationId, "resource", resourceId] as const,
};

export async function getSettings(
  scope: SettingsScope,
  id: number,
  signal?: AbortSignal,
): Promise<ProbeSettingsResponse[]> {
  const response =
    scope === "organization"
      ? await list3(id, { signal })
      : await list2(id, { signal });
  return response.data as ProbeSettingsResponse[];
}

export async function saveSettings(
  scope: SettingsScope,
  id: number,
  type: ProbeType,
  values: ProbeSettingsRequest,
) {
  if (scope === "organization") await put1(id, type, values);
  else await put(id, type, values);
}

export async function resetSettings(
  scope: SettingsScope,
  id: number,
  type: ProbeType,
) {
  if (scope === "organization") await delete3(id, type);
  else await delete1(id, type);
}
