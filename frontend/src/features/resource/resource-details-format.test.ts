import { describe, expect, it } from "vitest";
import {
  buildAvailabilityRange,
  formatDuration,
  formatPercent,
} from "./resource-details-format";

describe("resource details formatting", () => {
  it.each([
    ["24h", 24],
    ["7d", 7 * 24],
    ["30d", 30 * 24],
  ] as const)("builds an exact %s UTC range", (period, hours) => {
    const to = new Date("2026-09-13T12:00:00.000Z");
    const range = buildAvailabilityRange(period, to);
    expect(range.to).toBe(to.toISOString());
    expect(Date.parse(range.to) - Date.parse(range.from)).toBe(
      hours * 60 * 60 * 1000,
    );
  });

  it("distinguishes missing percentages from zero", () => {
    expect(formatPercent(null)).toBe("—");
    expect(formatPercent(undefined)).toBe("—");
    expect(formatPercent(0)).toBe("0%");
    expect(formatPercent(99.95)).toBe("99,95%");
  });

  it("formats server durations without recalculating metrics", () => {
    expect(formatDuration(0)).toBe("0 с");
    expect(formatDuration(90061)).toBe("1 д 1 ч");
  });
});
