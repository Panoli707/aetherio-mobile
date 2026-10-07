import { describe, expect, it } from "vitest";
import { formatRuntime } from "./mediaLabels.ts";

describe("formatRuntime", () => {
  it("separa el número y la unidad para las tarjetas de episodio", () => {
    expect(formatRuntime(26)).toBe("26 min");
    expect(formatRuntime(65)).toBe("1 h 5 min");
    expect(formatRuntime(60)).toBe("1 h");
  });

  it("normaliza duraciones en segundos y conserva el vacío cuando faltan", () => {
    expect(formatRuntime(1560)).toBe("26 min");
    expect(formatRuntime(undefined)).toBe("");
  });
});
