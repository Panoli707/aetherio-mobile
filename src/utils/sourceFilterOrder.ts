import type { MediaStream } from "../types/stream.ts";
import { animeSourcePriority } from "./streamPlaybackRanking.ts";

export interface SourceChip {
  name: string;
  count: number;
  /** Posicion segun el add-on dueño; menor va antes. */
  priority: number;
}

/**
 * Un chip sin streams no se puede atribuir a ningun add-on. Se manda al final
 * con este sentinel en vez de con prioridad 0: si no, los chips que todavia no
 * han respondido se colarian por delante de las fuentes del usuario, que es
 * justo lo que se quiere evitar.
 */
const NO_OWNER = Number.MAX_SAFE_INTEGER;

export function normalizeSourceKey(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
}

export interface SourceChipInput {
  /** Nombres de fuente que se anticipan, para mostrar el chip antes de que llegue su primer stream. */
  expectedSourceNames: readonly string[];
  streams: readonly MediaStream[];
  /** addonId -> si es un add-on integrado. */
  bundledById: ReadonlyMap<string, boolean>;
  isAnime: boolean;
  /**
   * Los chips vacios solo se van al final cuando ya no queda nada que esperar:
   * mientras un add-on esta respondiendo su chip esta en Pending, no en rojo, y
   * levantarlo al final haria que la fila saltase cada vez que uno arrive.
   */
  loading?: boolean;
  /** Resuelve el nombre del chip de un stream (indexer, proveedor o add-on). */
  extractSourceName: (stream: MediaStream) => string | null;
  /** Rango del add-on; menor antes. */
  priorityOf: (addonId: string | undefined, bundled: boolean | undefined) => number;
}

/**
 * Escalera del panel: UN solo numero por chip, para que las reglas no se pisen
 * entre si. Antes el orden de anime se evaluaba antes que la prioridad del
 * add-on, asi que en anime ganaba el orden de anime entero y lo que instalo el
 * usuario caia donde le tocara.
 *
 *   0  lo que instalo el usuario (y los scrapers): si eligio esa fuente, manda
 *      sobre cualquier otra regla, anime incluido.
 *   1  AnimeAV1: el primero del orden de anime, por delante de los integrados.
 *   2+ el escalon que da el add-on dueno (PenguPlay, DeepFlix, Torrentio...).
 *
 * Lo demas —los otros chips de anime y los add-ons sin puesto fijo— cae al
 * final con NO_OWNER y, dentro de ese monton, manda el orden de anime si es
 * anime y el volumen si no lo es.
 */
function chipTier(chip: SourceChip): number {
  if (chip.priority === 0) return 0;
  if (animeSourcePriority(chip.name) === 0) return 1;
  return chip.priority;
}

/**
 * Construye y ordena los chips del panel de seleccion de fuentes.
 *
 * El chip raramente coincide con el add-on que lo produjo —Torrentio se abre
 * en ThePiratebay, Yoru, Nyaa— asi que el orden sale del add-on dueno de cada
 * chip y no de como se llama. Desempata la cantidad de streams, que es el
 * proxy de que ese add-on respondio completo.
 *
 * Al terminar la carga los chips sin streams se van al final de la fila
 * (`loading`), por delante solo de la escalera.
 */
export function buildSourceChips({
  expectedSourceNames,
  streams,
  bundledById,
  isAnime,
  loading = false,
  extractSourceName,
  priorityOf,
}: SourceChipInput): SourceChip[] {
  const chips = new Map<string, SourceChip>();
  for (const name of expectedSourceNames) {
    const key = normalizeSourceKey(name);
    if (key && !chips.has(key)) chips.set(key, { name, count: 0, priority: NO_OWNER });
  }
  for (const stream of streams) {
    const name = extractSourceName(stream);
    if (!name) continue;
    const key = normalizeSourceKey(name);
    const chip = chips.get(key) ?? { name, count: 0, priority: NO_OWNER };
    chip.count++;
    // Varios add-ons pueden abrir el mismo chip: gana el mejor posicionado,
    // que es el unico que se ve en la fila.
    chip.priority = Math.min(chip.priority, priorityOf(stream.addonId, bundledById.get(stream.addonId ?? "")));
    chips.set(key, chip);
  }
  return [...chips.values()].sort((a, b) => {
    // Un chip en rojo (0 streams y ya no esta cargando) no sirve para elegir:
    // solo informa de que ese add-on no respondio. Se va al final de la fila,
    // por detras de todo lo que ofrece algo, aunque su add-on sea el mas
    // prioritario o el anime le exija el primer puesto.
    if (!loading) {
      const emptyOrder = Number(a.count === 0) - Number(b.count === 0);
      if (emptyOrder) return emptyOrder;
    }
    const tierOrder = chipTier(a) - chipTier(b);
    if (tierOrder) return tierOrder;
    // Mismo escalon: en anime manda el orden de fuentes de anime.
    if (isAnime) {
      const animeOrder = animeSourcePriority(a.name) - animeSourcePriority(b.name);
      if (animeOrder) return animeOrder;
    }
    return b.count - a.count || a.name.localeCompare(b.name);
  });
}