import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchDetailCollection } from "./detailCollections";

afterEach(() => vi.unstubAllGlobals());

describe("fetchDetailCollection", () => {
  it("uses AniList franchise relations and artwork without waiting on Jikan or TMDB lookups", async () => {
    const title = `AniList franchise test ${crypto.randomUUID()}`;
    let requestedUrl = "";
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      requestedUrl = String(input);
      return new Response(JSON.stringify({
      data: {
        Media: {
          relations: {
            edges: [{
              node: {
                id: 987654,
                idMal: 123456,
                format: "TV",
                seasonYear: 2024,
                description: "Related series",
                title: { english: "Related Anime", romaji: "Related Anime" },
                coverImage: { extraLarge: "https://example.com/poster.jpg", large: null },
                bannerImage: "https://example.com/banner.jpg",
              },
            }],
          },
        },
      },
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchDetailCollection({
      mediaId: "tmdb:9999999",
      mediaType: "anime",
      tmdbId: 9999999,
      title,
      tmdbData: { original_language: "ja", genres: [{ id: 16 }] },
    });

    expect(result?.items).toEqual([expect.objectContaining({
      id: "mal:123456",
      title: "Related Anime",
      type: "series",
      poster: "https://example.com/poster.jpg",
      backdrop: "https://example.com/banner.jpg",
      year: "2024",
    })]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(requestedUrl).toContain("graphql.anilist.co");
  });
});
