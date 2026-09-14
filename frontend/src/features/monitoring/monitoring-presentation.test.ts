import { describe, expect, it } from "vitest";
import { presentProbeResult } from "./monitoring-presentation";

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
});
