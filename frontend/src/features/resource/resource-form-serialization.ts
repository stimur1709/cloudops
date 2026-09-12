import type {
  CreateResourceRequest,
  ResourceConfig,
  UpdateResourceRequest,
} from "../../api/generated/model";
import type { ResourceFormValues } from "./resource-form";

export function serializeResourceConfig(
  values: ResourceFormValues,
): ResourceConfig {
  const config = values.config;
  switch (values.type) {
    case "SERVER":
      return {
        host: config.host?.trim() ?? "",
        port: config.port,
        sshPort: config.sshPort,
      };
    case "NETWORK_DEVICE":
      return {
        host: config.host?.trim() ?? "",
        managementPort: config.managementPort,
      };
    case "DATABASE":
      return {
        host: config.host?.trim() ?? "",
        port: config.port as number,
        database: config.database?.trim() ?? "",
      };
    case "SERVICE":
      return {
        url: config.url?.trim() ?? "",
        expectedStatus: config.expectedStatus,
      };
    case "OTHER":
      return {};
  }
}

export function buildResourceRequest(
  values: ResourceFormValues,
  organizationId: number,
): CreateResourceRequest | UpdateResourceRequest {
  return {
    name: values.name.trim(),
    type: values.type,
    status: values.status,
    organizationId,
    config: serializeResourceConfig(values),
  };
}
