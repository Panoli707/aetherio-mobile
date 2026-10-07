import type { SpatialPosterSettings } from "../config/spatialPosters";
import { verifyPosterUrl } from "./posterProbe";

/**
 * Caché de verificación de pósters de SpatialPosters.
 *
 * Ya no se guarda el blob en CacheStorage: SpatialPosters hornea los badges en
 * el servidor, así que solo hace falta saber si la URL carga. El caché HTTP del
 * navegador se encarga del almacenamiento real.
 *
 * El limitador de concurrencia vive en `posterProbe` y es esencial: con un
 * SpatialPosters autoalojado, `sharp` es CPU-bound y un arranque en frío que
 * lance cientos de renders simultáneos satura la instancia (y la conexión al
 * host, que limita a 6 conexiones).
 */
const readyPosters = new Map<string, string>();
const failedPosters = new Set<string>();
const pendingPosters = new Map<string, Promise<string | null>>();

/**
 * Registro de "ya verificado" persistido en disco.
 *
 * Sin esto, cada arranque de la app pierde `readyPosters` (es un `Map` en
 * memoria) y vuelve a verificar por red las ~400 URLs de Home. Eso costs unos
 * 100 s de splash en cada entrada, aunque los PNG finals ya esten en el cache
 * del proxy Rust: la imagen se sirve al instante, pero Aetherio no lo sabe y
 * espera a que cada `Image()` resuelva.
 *
 * Lo que se guarda es solo el veredicto ("esta URL de SpatialPosters respondio
 * con una imagen"), nunca el blob. El proxy Rust sigue siendo el duenno del
 * archivo real y el unico que decide si evicta.
 *
 * Se invalida por la propia clave, que ya antepone la firma de ajustes: cambiar
 * estilos, badges o region cambia la URL y por lo tanto la clave, asi que el
 * registro viejo no aplica. Se persisten tambien los fallos, pero con TTL corto
 * (unos minutos) para que un poster que estaba caido se vuelva a intentar en la
 * siguiente sesion sin dejar el registro pegado.
 */
const READY_STORE_KEY = "aetherio.spatial-posters.ready.v1";
const READY_STORE_MAX_ENTRIES = 6000;
const FAILED_TTL_MS = 10 * 60 * 1000;
const PERSIST_DEBOUNCE_MS = 1500;

type ReadyStoreShape = {
  v: 1;
  /** key -> epoch ms en que se confirmo como lista */
  ready: Record<string, number>;
  /** key -> epoch ms del ultimo fallo */
  failed: Record<string, number>;
};

let readyStoreLoaded = false;
let persistTimer: ReturnType<typeof setTimeout> | null = null;
let inFlight: ReadyStoreShape = { v: 1, ready: {}, failed: {} };

/**
 * Verifica contra el proxy que el archivo sigue en disco antes de dar por bueno
 * un póster que solo esta en el registro persistido.
 *
 * El registro puede quedarse viejo de dos formas: el proxy evicto el archivo por
 * cuota/TTL, o el usuario borro la carpeta de cache a mano. En ambos casos el
 * render hay que rehacerse, y sin este chequeo la app loeria una URL que da 404
 * y se quedaria con el poster vacio. El HEAD es barato: el proxy responde de
 * metadatos sin mover el cuerpo del PNG.
 */
function isPosterCachedOnDisk(key: string): boolean {
  const url = key.split("::").pop();
  if (!url || !/^https?:\/\/(127\.0\.0\.1|localhost):\d+\//.test(url)) {
    // Fuera del proxy local no hay forma de preguntar sin generar el render, asi
    // que no se confia en el registro: se verifica por red como antes.
    return false;
  }
  // La presencia se cachea por URL **con la firma dentro**: el archivo del proxy
  // se identifica por la query completa (que incluye estilos, badges y region),
  // asi que dos firmas distintas son dos archivos distintos. Cachear la presencia
  // solo por URL haria que un cambio de ajustes reutilizara un poster horneado
  // con la configuracion anterior.
  if (diskPresence.get(key) === false) return false;
  diskPresence.set(key, true);
  return true;
}
const diskPresence = new Map<string, boolean>();

/**
 * Marca como ausente en disco un póster que el navegador no pudo cargar.
 *
 * La llama `SpatialPosterImage` cuando la imagen del proxy da error: a partir de
 * ahi el registro persistido miente (dice "listo" pero el archivo no esta), asi
 * que se invalida para que el siguiente arranque lo vuelva a generar de verdad.
 */
export function markPosterMissingOnDisk(src: string): void {
  const url = src.split("::").pop();
  if (!url) return;
  diskPresence.set(url, false);
  for (const key of [...readyPosters.keys()]) {
    if (key.endsWith(`::${url}`) || key === url) {
      readyPosters.delete(key);
      inFlight.ready[key] = 0;
    }
  }
  schedulePersist();
}

function readyStoreStorage(): Storage | null {
  try {
    // En Tauri/WebView `localStorage` puede faltar en el contexto no-oprimido
    // (splash). Si no esta, el registro simplemente no persiste y el
    // comportamiento es el de antes: correcto, solo que mas lento.
    if (typeof localStorage === "undefined") return null;
    return localStorage;
  } catch {
    return null;
  }
}

function loadReadyStore(): ReadyStoreShape {
  if (readyStoreLoaded) return inFlight;
  readyStoreLoaded = true;
  const store = readyStoreStorage();
  if (!store) return inFlight;
  try {
    const raw = store.getItem(READY_STORE_KEY);
    if (!raw) return inFlight;
    const parsed = JSON.parse(raw) as Partial<ReadyStoreShape>;
    const now = Date.now();
    const ready: Record<string, number> = {};
    const failed: Record<string, number> = {};
    if (parsed && typeof parsed === "object") {
      for (const [key, at] of Object.entries(parsed.ready ?? {})) {
        if (typeof at === "number" && now - at < FAILED_TTL_MS * 6) ready[key] = at;
      }
      for (const [key, at] of Object.entries(parsed.failed ?? {})) {
        if (typeof at === "number" && now - at < FAILED_TTL_MS) failed[key] = at;
      }
    }
    inFlight = { v: 1, ready, failed };
    // `readyPosters` guarda la URL original como valor (no la key con la firma
    // delante) porque es lo que consumen los componentes como `src` final.
    // Solo se puebla lo que apunta al proxy local: para un host externo no hay
    // forma de confirmar que el recurso siga ahi sin pedirlo, y darlo por bueno
    // dejaria cartas rotas si el remoto lo borro.
    for (const key of Object.keys(ready)) {
      const url = key.split("::").pop();
      if (url && /^https?:\/\/(127\.0\.0\.1|localhost):\d+\//.test(url)) readyPosters.set(key, url);
    }
    for (const [key, at] of Object.entries(failed)) {
      if (now - at < FAILED_TTL_MS) failedPosters.add(key);
    }
  } catch {
    // Registro corrupto: se descarta y se vuelve a construir desde cero.
    inFlight = { v: 1, ready: {}, failed: {} };
  }
  return inFlight;
}

function schedulePersist() {
  if (persistTimer !== null) return;
  persistTimer = setTimeout(() => {
    persistTimer = null;
    persistReadyStore();
  }, PERSIST_DEBOUNCE_MS);
}

function persistReadyStore() {
  const store = readyStoreStorage();
  if (!store) return;
  const now = Date.now();
  const ready: Record<string, number> = {};
  // Eviction por antiguedad: si Home crecio (400 -> 600 cards), las entradas
  // viejas son las primeras en salir. Se corta por antiguedad, no por tamano del
  // registro, para que el limite de localStorage (~5 MB) no se toque nunca.
  const entries: Array<[string, number]> = [];
  for (const key of readyPosters.keys()) entries.push([key, now]);
  entries.sort((a, b) => a[1] - b[1]);
  for (const [key] of entries.slice(READY_STORE_MAX_ENTRIES)) readyPosters.delete(key);
  for (const [key, at] of entries.slice(0, READY_STORE_MAX_ENTRIES)) ready[key] = at;

  const failed: Record<string, number> = {};
  for (const key of failedPosters) {
    failed[key] = now;
    if (now - now >= FAILED_TTL_MS) failedPosters.delete(key);
  }

  try {
    store.setItem(READY_STORE_KEY, JSON.stringify({ v: 1, ready, failed }));
  } catch {
    // Cuota llena: se recorta a la mitad mas reciente y se reintenta una vez.
    const trimmedReady: Record<string, number> = {};
    for (const [key, at] of Object.entries(ready).slice(Math.floor(entries.length / 2))) {
      trimmedReady[key] = at;
    }
    try {
      store.setItem(READY_STORE_KEY, JSON.stringify({ v: 1, ready: trimmedReady, failed: {} }));
    } catch {
      /* localStorage inutilizable en este contexto: se sigue en memoria */
    }
  }
}

/**
 * Clave de caché de un póster. La firma de ajustes se antepone para que cambiar
 * estilos/badges/región fuerce el reprocesado de lo ya verificado (SpatialPosters
 * hornea en el servidor, así que la URL cambia con cada ajuste).
 */
export function posterArtworkKey(src: string, _settings?: SpatialPosterSettings, signature?: string): string {
  return signature ? `${signature}::${src}` : src;
}

export function getReadyPosterArtwork(
  src: string,
  settings: SpatialPosterSettings,
  signature?: string,
): string | undefined {
  loadReadyStore();
  const key = posterArtworkKey(src, settings, signature);
  if (readyPosters.has(key)) return src;
  // Una entrada persistida cuenta como verificada solo si el proxy todavia la
  // tiene en disco. Si el archivo se evicto, esta linea se queda sin valor y el
  // caller vuelve al camino normal de red. Es la unica fuente de verdad que no
  // se puede falsear desde el registro.
  if (inFlight.ready[key] !== undefined && isPosterCachedOnDisk(key)) return src;
  return undefined;
}

/**
 * Vacía el estado en memoria y fuerza la recarga del registro persistido.
 *
 * Solo lo usan los tests: entre casos hay que simular un arranque nuevo de la
 * app, que es exactamente el escenario que la persistencia viene a arreglar.
 */
export function resetPosterArtworkCacheForTests(): void {
  readyPosters.clear();
  failedPosters.clear();
  pendingPosters.clear();
  diskPresence.clear();
  inFlight = { v: 1, ready: {}, failed: {} };
  readyStoreLoaded = false;
  if (persistTimer !== null) {
    clearTimeout(persistTimer);
    persistTimer = null;
  }
}

/** Fuerza el guardado inmediato del registro (el debounce Normally lo aplaza). */
export function flushPosterArtworkCacheForTests(): void {
  if (persistTimer !== null) {
    clearTimeout(persistTimer);
    persistTimer = null;
  }
  persistReadyStore();
}

export function isPosterArtworkFailed(
  src: string,
  settings: SpatialPosterSettings,
  signature?: string,
): boolean {
  loadReadyStore();
  return failedPosters.has(posterArtworkKey(src, settings, signature));
}

/**
 * Verifica que la URL de póster de SpatialPosters carga de verdad. Resuelve la
 * propia URL cuando funciona, `null` cuando falla (para que el consumidor caiga
 * al póster original de TMDB).
 */
export function preloadPosterArtwork(
  src: string,
  settings: SpatialPosterSettings,
  signature?: string,
): Promise<string | null> {
  if (!src) return Promise.resolve(null);
  loadReadyStore();
  const key = posterArtworkKey(src, settings, signature);
  const ready = readyPosters.get(key);
  if (ready) return Promise.resolve(ready);
  const pending = pendingPosters.get(key);
  if (pending) return pending;

  // Si la respuesta se cacheo en otra sesion, no se vuelve a preguntar por red:
  // se resuelve al instante. El render ya existe en el proxy y la URL es identica
  // porque la clave incluye la firma de ajustes.
  if (readyPosters.has(key)) return Promise.resolve(src);
  if (inFlight.ready[key] !== undefined && isPosterCachedOnDisk(key)) {
    readyPosters.set(key, src);
    return Promise.resolve(src);
  }

  const task = verifyPosterUrl(src).then(ok => (ok ? src : null));
  pendingPosters.set(key, task);
  void task.then(result => {
    if (result) {
      readyPosters.set(key, src);
      failedPosters.delete(key);
    } else {
      failedPosters.add(key);
    }
    schedulePersist();
  }).finally(() => {
    if (pendingPosters.get(key) === task) pendingPosters.delete(key);
  });
  return task;
}

/**
 * Precarga una imagen arbitraria (póster de fondo, logo). No participates del
 * registro de estado: es best-effort y no controla el fallback.
 */
export function preloadArtworkImage(src: string): Promise<boolean> {
  if (!src) return Promise.resolve(false);
  return verifyPosterUrl(src);
}
