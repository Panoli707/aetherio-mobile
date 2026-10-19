import { describe, expect, it } from "vitest";
import {
  persistentTmdbCacheKey,
  persistentTmdbCacheTtlMs,
  persistentTmdbTextData,
} from "./tmdbTextCache";

describe("TMDB persistent text-cache policy", () => {
  it("builds the same key for direct and proxied API requests", () => {
    const direct = new URL(
      "https://api.themoviedb.org/3/movie/550?api_key=secret&language=es-ES&append_to_response=credits,keywords",
    );
    const proxied = new URL(
      "https://trkll.aetherio.workers.dev/api/tmdb/movie/550?language=es-ES&append_to_response=credits%2Ckeywords",
    );

    expect(persistentTmdbCacheKey(direct)).toBe(persistentTmdbCacheKey(proxied));
  });

  it("never persists the user's TMDB API key in the cache key", () => {
    const url = new URL("https://api.themoviedb.org/3/movie/550?api_key=secret-value&language=es-ES");
    const key = persistentTmdbCacheKey(url);

    expect(key).toBe("/movie/550?language=es-ES");
    expect(key).not.toContain("secret-value");
  });

  it("shares the text cache key when only the images append differs", () => {
    const withImages = new URL(
      "https://api.themoviedb.org/3/tv/1399?language=es-ES&append_to_response=credits,images&include_image_language=es,null",
    );
    const textOnly = new URL(
      "https://api.themoviedb.org/3/tv/1399?language=es-ES&append_to_response=credits",
    );
    expect(persistentTmdbCacheKey(withImages)).toBe(persistentTmdbCacheKey(textOnly));
  });

  it("excludes image assets and the TMDB images endpoint", () => {
    expect(persistentTmdbCacheKey(new URL("https://image.tmdb.org/t/p/w500/poster.jpg"))).toBeNull();
    expect(persistentTmdbCacheKey(new URL("https://api.themoviedb.org/3/movie/550/images"))).toBeNull();
    expect(persistentTmdbCacheKey(new URL("https://api.themoviedb.org/3/tv/1399/season/1")))
      .toBe("/tv/1399/season/1");
  });

  it("keeps frequently changing listings fresher than title metadata", () => {
    const search = persistentTmdbCacheTtlMs(new URL("https://api.themoviedb.org/3/search/movie?query=alien"));
    const detail = persistentTmdbCacheTtlMs(new URL("https://api.themoviedb.org/3/tv/1399/season/1"));

    expect(search).toBe(6 * 60 * 60 * 1_000);
    expect(detail).toBe(14 * 24 * 60 * 60 * 1_000);
  });

  it("strips image collections but retains text and image-path references", () => {
    expect(persistentTmdbTextData({
      name: "Serie",
      overview: "Descripción",
      poster_path: "/poster.jpg",
      images: { logos: [{ file_path: "/logo.png" }] },
      nested: [{ title: "Episodio", images: [{ file_path: "/still.jpg" }] }],
    })).toEqual({
      name: "Serie",
      overview: "Descripción",
      poster_path: "/poster.jpg",
      nested: [{ title: "Episodio" }],
    });
  });
});
