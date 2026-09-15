import { describe, expect, it } from "vitest";
import { scopedPreviousData } from "./scoped-previous-data";

describe("scopedPreviousData", () => {
  const keepHistory = scopedPreviousData<string[]>([
    "monitoring",
    11,
    "resource",
    7,
    "monitor",
    21,
    "history",
  ]);

  it("keeps a previous page only within the same entity scope", () => {
    expect(
      keepHistory(["old row"], {
        queryKey: [
          "monitoring",
          11,
          "resource",
          7,
          "monitor",
          21,
          "history",
          0,
          10,
          "desc",
        ],
      }),
    ).toEqual(["old row"]);
  });

  it("does not transfer rows across monitor, resource or organization", () => {
    const base = ["monitoring", 11, "resource", 7, "monitor", 21, "history", 0];
    for (const [index, replacement] of [
      [1, 22],
      [3, 8],
      [5, 31],
    ] as const) {
      const queryKey = [...base];
      queryKey[index] = replacement;
      expect(keepHistory(["old row"], { queryKey })).toBeUndefined();
    }
  });
});
