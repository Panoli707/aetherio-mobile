import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { invoke } from "@tauri-apps/api/core";
import { getCurrent, onOpenUrl } from "@tauri-apps/plugin-deep-link";
import { openUrl } from "@tauri-apps/plugin-opener";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { getCurrentDeepLinks, getPlaybackCapabilities, listenOpenUrls } from "./platform";
import { ensurePosterServer, getPosterCacheUrl, posterServerStatus, resolvePosterEndpoint, stopPosterServer } from "../services/posterServer";
import { clearDiscordActivity, setDiscordActivity, startDiscordRichPresence, stopDiscordRichPresence } from "../integrations/discordPresence";
import { buildSpatialPosterUrl, DEFAULT_SPATIAL_POSTER_SETTINGS, getEffectiveInstanceUrl, getSpatialPosterSettings, isSpatialPostersConfigured, setPosterCacheUrl } from "../config/spatialPosters";
import { getScraperSites, scrapeStreams } from "../services/scraperService";
import { addProviderRuntimeRepository, getProviderRuntimeRepositories, scrapeProviderRuntimes } from "../services/providerRuntimeService";
import { getGlobalCloudstreamRepositories } from "../services/cloudstreamRepositoryService";
import { getMediaExtensionInventory, scrapeMediaExtensions } from "../services/mediaExtensionService";
import { fetchYouTubeClip, getCachedClipInfo, getCachedFirstTrailerClipInfo } from "../services/youtubeClips";
import { completeOAuthAuthorization, getAccountToken, getAniListAccessToken, getOAuthProviders, getStoredAccount, isLocalModeEnabled, loginAccount, registerAccount, restoreAccountSession, startSocialLogin } from "../auth/authClient";
import { completeTraktAuthorization, getTraktAuthSnapshot, startTraktAuthorization, syncTraktNow } from "../trakt";
import { getLocalProfiles } from "../utils/localProfiles";
import { readPersistentTmdbCache, resetPersistentTmdbCacheForTests, writePersistentTmdbCache } from "../config/tmdbTextCache";
import WindowControls from "../components/layout/WindowControls";
import SettingsPage from "../pages/Settings";
import LibraryPage from "../pages/Library";

vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn() }));
vi.mock("@tauri-apps/plugin-deep-link", () => ({ getCurrent: vi.fn(), onOpenUrl: vi.fn() }));
vi.mock("@tauri-apps/plugin-opener", () => ({ openUrl: vi.fn() }));
vi.mock("../navigation/spatialNav.ts", () => ({ installSpatialNavigation: vi.fn() }));

const storage = vi.hoisted(() => {
  const values = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  });
  return values;
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubEnv("TAURI_ENV_PLATFORM", "android");
  vi.stubGlobal("window", { __TAURI_INTERNALS__: {}, setTimeout, clearTimeout });
  vi.stubGlobal("navigator", { userAgent: "Desktop user agent" });
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  });
  vi.stubGlobal("fetch", vi.fn());
  storage.clear();
  resetPersistentTmdbCacheForTests();
});

afterEach(() => {
  resetPersistentTmdbCacheForTests();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("Android catalog-only runtime", () => {
  it("does not offer account connection in the local library", () => {
    const markup = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(LibraryPage)));
    expect(markup).not.toContain("Conectar cuenta");
    expect(markup).not.toContain("Sincronizar ahora");
  });

  it("does not start or query desktop poster and Discord services", async () => {
    expect(await ensurePosterServer("http://localhost:3000")).toBeNull();
    expect(await getPosterCacheUrl()).toBeNull();
    expect(await posterServerStatus()).toBeNull();
    await stopPosterServer();
    await startDiscordRichPresence();
    await stopDiscordRichPresence();
    await setDiscordActivity({ details: "Title", state: "Catalog" });
    await clearDiscordActivity();
    setPosterCacheUrl("http://localhost:1234");
    expect(getSpatialPosterSettings().enabled).toBe(false);
    expect(isSpatialPostersConfigured(DEFAULT_SPATIAL_POSTER_SETTINGS)).toBe(false);
    expect(getEffectiveInstanceUrl(DEFAULT_SPATIAL_POSTER_SETTINGS)).toBe("");
    expect(await resolvePosterEndpoint(DEFAULT_SPATIAL_POSTER_SETTINGS)).toBe("");
    expect(buildSpatialPosterUrl(1, "movie", DEFAULT_SPATIAL_POSTER_SETTINGS)).toBeUndefined();
    expect(invoke).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns no scraper/provider/clip results without invoking unavailable backends", async () => {
    expect(await scrapeStreams("Title", "movie")).toEqual([]);
    expect(await getScraperSites()).toEqual([]);
    expect(await getProviderRuntimeRepositories()).toEqual([]);
    expect(await getGlobalCloudstreamRepositories([])).toEqual([]);
    expect(await getMediaExtensionInventory()).toEqual({ installed: [], errors: [] });
    expect(await scrapeMediaExtensions({ id: "tmdb:1", type: "movie" }, "Title")).toEqual([]);
    const onBatchStatus = vi.fn();
    expect(await scrapeProviderRuntimes({ id: "tmdb:1", type: "movie" }, "Title", undefined, undefined, undefined, onBatchStatus)).toEqual([]);
    expect(onBatchStatus).not.toHaveBeenCalled();
    await expect(addProviderRuntimeRepository("https://example.com/manifest.json")).rejects.toThrow("Android");
    const item = { id: "tmdb:1", type: "movie", name: "Title" };
    storage.set("aetherio-youtube-clips-v4", JSON.stringify({
      "clip:movie:tmdb:1": { videoId: "cached", source: "youtube", duration: 90, fetchedAt: Date.now(), fallbacks: [] },
    }));
    expect(getCachedClipInfo(item)).toBeNull();
    expect(getCachedFirstTrailerClipInfo(item)).toBeNull();
    expect(await fetchYouTubeClip(item)).toBeNull();
    expect(await fetchYouTubeClip(item, { firstTrailerOnly: true })).toBeNull();
    expect(invoke).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("ignores persisted accounts and rejects login/OAuth/Trakt without network or credentials", async () => {
    storage.set("aetherio-account-user-v1", JSON.stringify({ id: "remote", email: "a@example.com", displayName: "Remote" }));
    expect(isLocalModeEnabled()).toBe(true);
    expect(getStoredAccount()).toBeNull();
    expect(await restoreAccountSession()).toBeNull();
    expect(await getAccountToken()).toBeNull();
    expect(await getAniListAccessToken()).toBeNull();
    await expect(loginAccount("a@example.com", "password")).rejects.toThrow("Android");
    await expect(registerAccount({ displayName: "User", email: "a@example.com", password: "password" })).rejects.toThrow("Android");
    await expect(getOAuthProviders()).rejects.toThrow("Android");
    await expect(startSocialLogin("google")).rejects.toThrow("Android");
    await expect(completeOAuthAuthorization("aetherio://auth/callback#access_token=secret")).rejects.toThrow("Android");
    await expect(startTraktAuthorization()).rejects.toThrow("Android");
    await expect(completeTraktAuthorization("aetherio://trakt/callback?code=secret")).rejects.toThrow("Android");
    expect(getTraktAuthSnapshot().connected).toBe(false);
    expect((await syncTraktNow()).imported).toBe(0);
    getLocalProfiles();
    expect(storage.has("aetherio-profile-space-v2:local:profiles")).toBe(true);
    expect(invoke).not.toHaveBeenCalled();
    expect(openUrl).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("does not access the absent deep-link plugin", async () => {
    expect(await getCurrentDeepLinks()).toEqual([]);
    (await listenOpenUrls(vi.fn()))();
    expect(getCurrent).not.toHaveBeenCalled();
    expect(onOpenUrl).not.toHaveBeenCalled();
  });

  it("keeps native playback capabilities and TMDB text caching available", async () => {
    vi.useFakeTimers();
    window.setTimeout = setTimeout as unknown as typeof window.setTimeout;
    window.clearTimeout = clearTimeout;
    vi.mocked(invoke).mockResolvedValueOnce({ backend: "none", mpvBundled: false });
    expect(await getPlaybackCapabilities()).toEqual({ backend: "none", mpvBundled: false });
    const entry = { data: { title: "Title" }, expiresAt: Date.now() + 60_000 };
    vi.mocked(invoke).mockResolvedValueOnce(entry);
    expect(await readPersistentTmdbCache("/movie/1")).toEqual(entry);
    writePersistentTmdbCache("/movie/2", { title: "Another title" }, 60_000);
    vi.mocked(invoke).mockResolvedValueOnce(undefined);
    await vi.runAllTimersAsync();
    expect(vi.mocked(invoke).mock.calls.map(([command]) => command)).toEqual([
      "playback_capabilities", "tmdb_text_cache_get", "tmdb_text_cache_set_many",
    ]);
  });

  it("renders local settings even for an account query and hides window controls", () => {
    expect(renderToStaticMarkup(createElement(WindowControls))).toBe("");
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ["/settings?tab=trakt"] }, createElement(SettingsPage)));
    expect(html).toContain("Perfiles locales");
    expect(html).toContain("Reproduccion Android pendiente");
    expect(html).not.toContain("Iniciar sesi");
    expect(html).not.toContain("Discord");
    expect(html).not.toContain("Fuentes");
    expect(invoke).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("preserves desktop poster, Discord and scraper commands", async () => {
    vi.stubEnv("TAURI_ENV_PLATFORM", "windows");
    vi.mocked(invoke).mockResolvedValue(undefined);
    await ensurePosterServer("http://localhost:3000");
    await startDiscordRichPresence();
    await scrapeStreams("Title", "movie");
    expect(vi.mocked(invoke).mock.calls.map(([command]) => command)).toEqual([
      "start_posters_server", "discord_rpc_start", "scrape_streams",
    ]);
    expect(isSpatialPostersConfigured(DEFAULT_SPATIAL_POSTER_SETTINGS)).toBe(true);
    expect(buildSpatialPosterUrl(1, "movie", DEFAULT_SPATIAL_POSTER_SETTINGS)).toContain("http://localhost:3000/api/poster/movie/1?");
  });
});
