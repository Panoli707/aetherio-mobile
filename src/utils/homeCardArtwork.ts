import { getScopedStorageKey, LOCAL_PROFILES_CHANGED_EVENT } from "./localProfiles";
import { getEffectiveInstanceUrl, getSpatialPosterSettings, spatialPosterSignature } from "../config/spatialPosters";
import { canonicalPosterUrl, preloadArtworkImage, preloadPosterArtwork } from "../services/posterArtworkCache";

export type HomeCardArtworkMode = "background" | "poster" | "logo";

const HOME_CARD_ARTWORK_KEY = "aetherio-home-card-artwork-v1";
export const HOME_CARD_ARTWORK_CHANGED_EVENT = "aetherio-home-card-artwork-changed";

function artworkKey(mode: HomeCardArtworkMode, type: string, id: string) {
  const normalizedType = type === "tv" ? "series" : type;
  return `${mode}:${normalizedType}:${id}`;
}

/**
 * Al guardar se persiste la forma canónica (`/api/poster/...`): el puerto del
 * proxy Rust es efímero, así que una URL absoluta del proxy quedaría muerta en
 * el siguiente arranque (la imagen de la tarjeta rompería hasta desmarcarla).
 * Un valor que no venga del proxy se guarda tal cual.
 */
function storePosterUrl(url: string) {
  const canonical = canonicalPosterUrl(url);
  return canonical.startsWith("/api/poster/") ? canonical : url;
}

/**
 * Al leer se rebasa la forma canónica contra la instancia efectiva de esta
 * sesión. Se rebasa aquí (y no solo al guardar) porque los valores guardados
 * por versiones anteriores son URLs absolutas con el puerto muerto, y porque el
 * puerto cambia en cada arranque. Una ruta relativa no se puede usar como `src`
 * porque resolvería contra el origen del WebView, no contra el proxy.
 */
function readPosterUrl(stored: string) {
  const canonical = canonicalPosterUrl(stored);
  if (canonical === stored || !canonical.startsWith("/api/poster/")) return stored;
  const base = getEffectiveInstanceUrl();
  return base ? `${base}${canonical}` : stored;
}

function readArtworkMap() {
  if (typeof window === "undefined" || typeof window.localStorage === "undefined") return {};
  if (artworkMapCache !== null) return artworkMapCache;
  try {
    const raw = window.localStorage.getItem(getScopedStorageKey(HOME_CARD_ARTWORK_KEY));
    artworkMapCache = raw ? JSON.parse(raw) as Record<string, string> : {};
  } catch {
    artworkMapCache = {};
  }
  return artworkMapCache;
}

let artworkMapCache: Record<string, string> | null = null;

if (typeof window !== "undefined") {
  window.addEventListener("storage", (event) => {
    if (event.key === getScopedStorageKey(HOME_CARD_ARTWORK_KEY)) artworkMapCache = null;
  });
  window.addEventListener(HOME_CARD_ARTWORK_CHANGED_EVENT, () => {
    artworkMapCache = null;
  });
  window.addEventListener(LOCAL_PROFILES_CHANGED_EVENT, () => {
    artworkMapCache = null;
  });
}

export function readHomeCardArtwork(
  mode: HomeCardArtworkMode,
  type: string,
  id: string,
  fallback?: string,
) {
  const stored = readArtworkMap()[artworkKey(mode, type, id)] as string | undefined;
  return stored === undefined ? fallback : readPosterUrl(stored);
}

export function writeHomeCardArtwork(
  mode: HomeCardArtworkMode,
  type: string,
  id: string,
  url: string,
) {
  if (typeof window === "undefined" || typeof window.localStorage === "undefined") return;
  try {
    const storageKey = getScopedStorageKey(HOME_CARD_ARTWORK_KEY);
    const map = readArtworkMap();
    const storedUrl = mode === "poster" ? storePosterUrl(url) : url;
    map[artworkKey(mode, type, id)] = storedUrl;
    window.localStorage.setItem(storageKey, JSON.stringify(map));
    artworkMapCache = map;
    window.dispatchEvent(new CustomEvent(HOME_CARD_ARTWORK_CHANGED_EVENT, {
      detail: { mode, type, id, url: storedUrl },
    }));
    if (mode === "poster") {
      const settings = getSpatialPosterSettings();
      // El preload verifica la URL absoluta recién elegida (la clave de caché ya
      // normaliza el puerto); una ruta relativa no resolvería contra el proxy.
      void preloadPosterArtwork(url, settings, spatialPosterSignature(settings));
    } else {
      void preloadArtworkImage(url);
    }
  } catch {
    // User-selected card artwork is best-effort.
  }
}
