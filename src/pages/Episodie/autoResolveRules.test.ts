import { describe, expect, it } from "vitest";
import { shouldAutoResolveOnEntry } from "./autoResolveRules";

const MANUAL = {
  continueRequested: false,
  autoplayRequested: false,
  sourceSelectionMode: "manual",
  cacheSignal: true,
} as const;

describe("shouldAutoResolveOnEntry", () => {
  it("'Continuar' siempre revela el panel, haya cache o no", () => {
    expect(shouldAutoResolveOnEntry({ ...MANUAL, continueRequested: true })).toBe(false);
    expect(shouldAutoResolveOnEntry({ ...MANUAL, continueRequested: true, cacheSignal: false })).toBe(false);
    // Ni por el epiteto del modo ni por un autoplay colado.
    expect(shouldAutoResolveOnEntry({
      ...MANUAL,
      continueRequested: true,
      sourceSelectionMode: "first",
    })).toBe(false);
  });

  it("'Continuar' tampoco se resuelve por una preferencia de fuente", () => {
    expect(shouldAutoResolveOnEntry({
      continueRequested: true,
      autoplayRequested: true,
      sourceSelectionMode: "manual",
      cacheSignal: true,
    })).toBe(false);
  });

  it("autoplay y 'primera fuente' se resuelven solas", () => {
    expect(shouldAutoResolveOnEntry({ ...MANUAL, autoplayRequested: true, cacheSignal: false })).toBe(true);
    expect(shouldAutoResolveOnEntry({ ...MANUAL, sourceSelectionMode: "first", cacheSignal: false })).toBe(true);
  });

  it("'manual' solo se resuelve si hay cache", () => {
    expect(shouldAutoResolveOnEntry(MANUAL)).toBe(true);
    expect(shouldAutoResolveOnEntry({ ...MANUAL, cacheSignal: false })).toBe(false);
  });
});