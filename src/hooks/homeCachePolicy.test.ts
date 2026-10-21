import { describe, expect, it } from "vitest";
import {
  isReusableHomeCache,
  normalizeDailyHeroSignature,
  normalizeDailyRowsSignature,
} from "./homeCachePolicy";

describe("home cache startup policy", () => {
  it("reuses a snapshot for up to seven days without extending its age", () => {
    const now = 1_000_000_000_000;

    expect(isReusableHomeCache(now - 6 * 24 * 60 * 60 * 1_000, now)).toBe(true);
    expect(isReusableHomeCache(now - 8 * 24 * 60 * 60 * 1_000, now)).toBe(false);
    expect(isReusableHomeCache(0, now)).toBe(false);
  });

  it("treats only the daily ranking component of catalog signatures as refreshable", () => {
    expect(normalizeDailyRowsSignature("rails|addon|url|d:2026-10-19|orient:both"))
      .toBe(normalizeDailyRowsSignature("rails|addon|url|d:2026-10-20|orient:both"));
    expect(normalizeDailyRowsSignature("rails|addon-a|d:2026-10-20"))
      .not.toBe(normalizeDailyRowsSignature("rails|addon-b|d:2026-10-20"));
    expect(normalizeDailyRowsSignature("rails|aetherio-starter|2026-10-19|tmdb"))
      .toBe(normalizeDailyRowsSignature("rails|aetherio-starter|2026-10-20|tmdb"));
  });

  it("reuses yesterday's hero snapshot only when the remaining settings match", () => {
    expect(normalizeDailyHeroSignature("2026-10-19|tmdb|no-mdb|hero-v5"))
      .toBe(normalizeDailyHeroSignature("2026-10-20|tmdb|no-mdb|hero-v5"));
    expect(normalizeDailyHeroSignature("2026-10-19|tmdb|no-mdb|hero-v5"))
      .not.toBe(normalizeDailyHeroSignature("2026-10-20|no-tmdb|no-mdb|hero-v5"));
  });
});
