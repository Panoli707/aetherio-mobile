import { invoke } from "@tauri-apps/api/core";

export interface PersistentTmdbCacheEntry {
  data: unknown;
  expiresAt: number;
}

interface PendingCacheWrite extends PersistentTmdbCacheEntry {
  key: string;
}

const DAY_MS = 24 * 60 * 60 * 1_000;
const SEARCH_CACHE_TTL_MS = 6 * 60 * 60 * 1_000;
const DETAIL_CACHE_TTL_MS = 14 * DAY_MS;
const MAX_SESSION_ENTRIES = 1_000;
const MAX_ENTRY_BYTES = 2 * 1024 * 1024;
const WRITE_DEBOUNCE_MS = 350;
const WRITE_BATCH_SIZE = 8;

// La memoria evita un IPC por uso repetido durante la sesion. Los misses
// tambien se memorizan: solo el primer uso de una clave consulta AppData.
const sessionCache = new Map<string, PersistentTmdbCacheEntry | null>();
const pendingReads = new Map<string, Promise<PersistentTmdbCacheEntry | null>>();
const pendingWrites = new Map<string, PendingCacheWrite>();
let flushTimer: number | null = null;
let nativeCacheUnavailable = false;

function isTauriRuntime() {
  return typeof window !== "undefined"
    && (Reflect.has(window, "__TAURI_INTERNALS__") || Reflect.has(window, "__TAURI__"));
}

export function normalizedTmdbPath(url: URL) {
  return url.pathname
    .replace(/^\/3(?=\/)/, "")
    .replace(/^\/api\/tmdb(?=\/)/, "")
    .replace(/\/$/, "") || "/";
}

/** Clave estable entre el acceso directo a TMDB y el proxy de Aetherio. */
export function persistentTmdbCacheKey(url: URL): string | null {
  if (url.hostname.toLowerCase() === "image.tmdb.org") return null;
  const path = normalizedTmdbPath(url);
  // Los endpoints que devuelven colecciones de imágenes no forman parte de la
  // caché de texto. Las rutas poster_path/backdrop_path son solo referencias
  // en JSON; los bytes de imagen se descargan por separado desde image.tmdb.org.
  if (/(?:^|\/)images$/.test(path)) return null;

  const params = new URLSearchParams(url.searchParams);
  params.delete("api_key");
  params.delete("include_image_language");
  const appended = params.get("append_to_response");
  if (appended) {
    const textResources = appended.split(",").map(resource => resource.trim())
      .filter(resource => resource && resource.toLowerCase() !== "images");
    if (textResources.length) params.set("append_to_response", textResources.join(","));
    else params.delete("append_to_response");
  }
  params.sort();
  const query = params.toString();
  return `${path}${query ? `?${query}` : ""}`;
}

/** Listas y búsquedas cambian más deprisa que los metadatos de una obra. */
export function persistentTmdbCacheTtlMs(url: URL): number {
  const path = normalizedTmdbPath(url);
  const isDynamicListing = /^\/(?:search|discover|trending)(?:\/|$)/.test(path)
    || /\/(?:popular|now_playing|upcoming|airing_today|on_the_air|top_rated)$/.test(path)
    || /\/changes$/.test(path);
  return isDynamicListing ? SEARCH_CACHE_TTL_MS : DETAIL_CACHE_TTL_MS;
}

/** Copia persistible de la respuesta: se omiten arrays/objetos de imágenes. */
export function persistentTmdbTextData(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(persistentTmdbTextData);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>)
      .filter(([key]) => key.toLowerCase() !== "images")
      .map(([key, nested]) => [key, persistentTmdbTextData(nested)]),
  );
}

function remember(key: string, entry: PersistentTmdbCacheEntry | null) {
  sessionCache.delete(key);
  sessionCache.set(key, entry);
  while (sessionCache.size > MAX_SESSION_ENTRIES) {
    const oldest = sessionCache.keys().next().value;
    if (typeof oldest !== "string") break;
    sessionCache.delete(oldest);
  }
}

export async function readPersistentTmdbCache(
  key: string,
): Promise<PersistentTmdbCacheEntry | null> {
  if (!key || nativeCacheUnavailable || !isTauriRuntime()) return null;

  if (sessionCache.has(key)) {
    const cached = sessionCache.get(key) ?? null;
    if (cached && cached.expiresAt <= Date.now()) {
      remember(key, null);
      return null;
    }
    // Refresh LRU order without extending the persisted TTL.
    remember(key, cached);
    return cached;
  }

  const pending = pendingReads.get(key);
  if (pending) return pending;

  const request = invoke<PersistentTmdbCacheEntry | null>("tmdb_text_cache_get", { key })
    .then(entry => {
      const valid = entry && Number.isFinite(entry.expiresAt) && entry.expiresAt > Date.now()
        ? entry
        : null;
      remember(key, valid);
      return valid;
    })
    .catch(error => {
      nativeCacheUnavailable = true;
      if (import.meta.env.DEV) console.warn("[TMDB cache] No se pudo leer AppData:", error);
      return null;
    })
    .finally(() => {
      if (pendingReads.get(key) === request) pendingReads.delete(key);
    });
  pendingReads.set(key, request);
  return request;
}

export function writePersistentTmdbCache(key: string, data: unknown, ttlMs: number) {
  if (!key || ttlMs <= 0 || nativeCacheUnavailable || !isTauriRuntime()) return;

  const textData = persistentTmdbTextData(data);
  let serialized: string;
  try {
    serialized = JSON.stringify(textData);
  } catch {
    return;
  }
  if (new TextEncoder().encode(serialized).byteLength > MAX_ENTRY_BYTES) return;

  const entry = { data: textData, expiresAt: Date.now() + ttlMs };
  remember(key, entry);
  pendingWrites.set(key, { key, ...entry });
  if (flushTimer !== null) return;
  flushTimer = window.setTimeout(() => {
    flushTimer = null;
    void flushPersistentWrites();
  }, WRITE_DEBOUNCE_MS);
}

async function flushPersistentWrites() {
  const entries = Array.from(pendingWrites.values());
  pendingWrites.clear();
  for (let index = 0; index < entries.length; index += WRITE_BATCH_SIZE) {
    try {
      await invoke("tmdb_text_cache_set_many", {
        entries: entries.slice(index, index + WRITE_BATCH_SIZE),
      });
    } catch (error) {
      // El fallo de persistencia no debe impedir la reproducción ni la petición.
      if (import.meta.env.DEV) console.warn("[TMDB cache] No se pudo guardar en AppData:", error);
    }
  }
}

export function resetPersistentTmdbCacheForTests() {
  sessionCache.clear();
  pendingReads.clear();
  pendingWrites.clear();
  if (flushTimer !== null && typeof window !== "undefined") window.clearTimeout(flushTimer);
  flushTimer = null;
  nativeCacheUnavailable = false;
}
