import { describe, expect, it } from "vitest";
import {
  formatCompactMonitorTime,
  getMonitorTypeLabel,
  getProbeResultLabel,
  presentProbeResult,
} from "./monitoring-presentation";

describe("monitoring presentation", () => {
  it("uses the monitor type to present a typed result", () => {
    const result = presentProbeResult("HTTP_CHECK", {
      success: false,
      data: {
        url: "https://payments.example.test",
        statusCode: 503,
        expectedStatus: 204,
        responseTimeMs: 42,
        matchedExpectedStatus: false,
      },
    });

    expect(result.outcome).toBe("failure");
    expect(result.fields).toEqual(
      expect.arrayContaining([
        { label: "HTTP-статус", value: "503" },
        { label: "Ожидаемый статус", value: "204" },
        { label: "Время ответа", value: "42 мс" },
      ]),
    );
  });

  it("presents execution errors without inventing result fields", () => {
    expect(
      presentProbeResult("PING", {
        success: false,
        error: { code: "TIMEOUT", message: "Probe timed out" },
      }),
    ).toEqual({
      outcome: "failure",
      summary: "Probe timed out",
      errorCode: "TIMEOUT",
      fields: [],
    });
  });

  it("uses friendly monitor names and compact overview states", () => {
    expect(getMonitorTypeLabel("HTTP_CHECK")).toBe("HTTP");
    expect(getMonitorTypeLabel("PING")).toBe("Ping");
    expect(getProbeResultLabel(null)).toBe("Не выполнялась");
    expect(getProbeResultLabel({ success: true })).toBe("Успешно");
    expect(
      getProbeResultLabel({
        success: false,
        error: { code: "TIMEOUT", message: "Timed out" },
      }),
    ).toBe("Ошибка");
  });

  it("formats same-day timestamps without repeating date and timezone", () => {
    const value = "2026-09-14T10:05:00Z";
    expect(
      formatCompactMonitorTime(value, new Date("2026-09-14T12:00:00Z")),
    ).toMatch(/^Сегодня, /);
  });
});
