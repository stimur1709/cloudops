import { describe, expect, it } from "vitest";
import {
  getResourceStatusLabel,
  getResourceTypeLabel,
  resourceTypeLabels,
} from "../../components/resource-labels";

describe("resource presentation mappings", () => {
  it("localizes every resource type used by the API", () => {
    expect(resourceTypeLabels).toEqual({
      NETWORK_DEVICE: "Сетевое устройство",
      SERVER: "Сервер",
      DATABASE: "База данных",
      SERVICE: "Сервис",
      OTHER: "Другое",
    });
  });

  it("does not leak unknown user-facing enum values", () => {
    expect(getResourceTypeLabel("FUTURE_TYPE")).toBe("Неизвестный тип");
    expect(getResourceStatusLabel("FUTURE_STATUS")).toBe("Неизвестен");
  });
});
