import type {
  ResourceResponseStatus,
  ResourceResponseType,
} from "../api/generated/model";

export const resourceTypeLabels = {
  NETWORK_DEVICE: "Сетевое устройство",
  SERVER: "Сервер",
  DATABASE: "База данных",
  SERVICE: "Сервис",
  OTHER: "Другое",
} as const satisfies Record<ResourceResponseType, string>;

export const resourceStatusLabels = {
  ACTIVE: "Активен",
  INACTIVE: "Неактивен",
} as const satisfies Record<ResourceResponseStatus, string>;

export function getResourceTypeLabel(value?: string) {
  return resourceTypeLabels[value as ResourceResponseType] ?? "Неизвестный тип";
}

export function getResourceStatusLabel(value?: string) {
  return resourceStatusLabels[value as ResourceResponseStatus] ?? "Неизвестен";
}
