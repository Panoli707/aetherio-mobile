import { describe, expect, it } from "vitest";
import { buildSourceChips } from "./sourceFilterOrder.ts";
import { sourceFilterPriority } from "../store/addonStore.ts";
import { animeSourcePriority } from "./streamPlaybackRanking.ts";
import type { MediaStream } from "../types/stream.ts";

const DEEPFLIX = "org.portal.free";
const PENGUPLAY = "com.penguplay";
const TORRENTIO = ["com.stre", "mio.torrentio.addon"].join("");
const HDHUB = "com.stremio.HdHub";
const ANIMES = "com.animeflv.stremio.addon";

const bundledIds = [DEEPFLIX, PENGUPLAY, TORRENTIO, HDHUB, ANIMES];
const CUSTOM = "com.miaddon.favorito";

/**
 * `bundledById` sale de la lista de add-ons instalada, asi que un add-on del
 * usuario aparece ahi con `bundled: false`. Un id que no esta en el mapa es un
 * origen que Aetherio no conoce (scraper, extension de media) y no debe ganar
 * el primer puesto por el simple hecho de no aparecer.
 */
const installed = new Map<string, boolean>([
  ...bundledIds.map(id => [id, true] as [string, boolean]),
  [CUSTOM, false],
]);

/**
 * Streams de ejemplo. `name` es lo que devuelve `extractSourceName`: para
 * Torrentio eso es el indexer, no el add-on, y es justo el caso que obliga a
 * ordenar por add-on dueno en vez de por nombre de chip.
 */
function stream(addonId: string, chipName: string, count = 1): MediaStream[] {
  return Array.from({ length: count }, (_, index) => ({
    id: `${addonId}:${chipName}:${index}`,
    addonId,
    addonName: chipName,
    name: chipName,
  } as MediaStream));
}

function chips(streamGroups: Array<[string, string, number]>, isAnime = false) {
  const streams = streamGroups.flatMap(([addonId, chip, count]) => stream(addonId, chip, count));
  return buildSourceChips({
    expectedSourceNames: [],
    streams,
    bundledById: installed,
    isAnime,
    extractSourceName: item => item.name ?? null,
    priorityOf: sourceFilterPriority,
  }).map(chip => chip.name);
}

/**
 * Igual que `chips`, pero con fuentes anticipadas en vez de streams: es el caso
 * de un add-on que todavia no ha respondido y solo existe como chip vacio.
 */
function chipsWithPending(
  entries: Array<{ name: string; streams?: number; priority?: number }>,
  { isAnime = false, loading = false } = {},
) {
  return buildSourceChips({
    expectedSourceNames: entries.filter(entry => !entry.streams).map(entry => entry.name),
    streams: entries.flatMap(entry => stream(entry.priority === 0 ? CUSTOM : TORRENTIO, entry.name, entry.streams ?? 0)),
    bundledById: installed,
    isAnime,
    loading,
    extractSourceName: item => item.name ?? null,
    priorityOf: sourceFilterPriority,
  }).map(chip => chip.name);
}

describe("orden del panel de fuentes", () => {
  it("lo que instalo el usuario va primero, aunque traiga menos streams", () => {
    expect(chips([
      [TORRENTIO, "ThePiratebay", 40],
      [CUSTOM, "Mi fuente", 1],
    ])).toEqual(["Mi fuente", "ThePiratebay"]);
  });

  it("un origen desconocido no se cuela por delante de los add-ons", () => {
    // Streams de scraper o de una extension de media llegan con un addonId que
    // no esta en la lista de add-ons. Sin este caso, "no integrado" y "no
    // registrado" se confundian y todo lo desconocido se iba al primer puesto.
    expect(chips([
      ["scraper", "SitioRaro", 99],
      [DEEPFLIX, "DeepFlix", 1],
    ])).toEqual(["DeepFlix", "SitioRaro"]);
  });

  it("respeta el orden penguplay, deepflix, torrentio, hdhub", () => {
    expect(chips([
      [HDHUB, "HdHub", 30],
      [DEEPFLIX, "DeepFlix", 1],
      [PENGUPLAY, "PenguPlay", 25],
      [TORRENTIO, "ThePiratebay", 20],
    ])).toEqual(["PenguPlay", "DeepFlix", "ThePiratebay", "HdHub"]);
  });

  it("agrupa cada chip bajo su add-on aunque el chip sea un indexer", () => {
    // Los dos indexers son de Torrentio, asi que van juntos tras DeepFlix y
    // antes que HdHub, sin importar cuantos streams traigan.
    expect(chips([
      [HDHUB, "HdHub", 24],
      [TORRENTIO, "ThePiratebay", 49],
      [TORRENTIO, "Yoru", 18],
      [DEEPFLIX, "DeepFlix", 2],
    ])).toEqual(["DeepFlix", "ThePiratebay", "Yoru", "HdHub"]);
  });

  it("los addons integrados sin puesto fijo quedan al final, ordenados por volumen", () => {
    expect(chips([
      ["com.otro.integrado", "StremVerse", 5],
      ["com.otro.integrado2", "AIOStreams", 30],
      [DEEPFLIX, "DeepFlix", 1],
    ])).toEqual(["DeepFlix", "AIOStreams", "StremVerse"]);
  });

  it("un chip sin streams todavia no se cuela delante de las fuentes del usuario", () => {
    // `loading: true` porque el chip de PenguPlay todavia no ha respondido: es
    // Pending, no rojo. Al terminar la carga si baja al final, pero no puede
    // adelantarse a la fuente del usuario mientras espera.
    const list = buildSourceChips({
      expectedSourceNames: ["PenguPlay", "Mi fuente"],
      streams: stream("com.miaddon.favorito", "Mi fuente", 1),
      bundledById: installed,
      isAnime: false,
      loading: true,
      extractSourceName: item => item.name ?? null,
      priorityOf: sourceFilterPriority,
    }).map(chip => chip.name);
    expect(list).toEqual(["Mi fuente", "PenguPlay"]);
  });
});

describe("orden en anime", () => {
  it("AnimeAV1 va primero entre las fuentes de anime", () => {
    expect(animeSourcePriority("AnimeAV1")).toBeLessThan(animeSourcePriority("AnimES"));
  });

  it("la escalera manda sobre el orden de anime: AnimeAV1, PenguPlay, DeepFlix", () => {
    // La escalera es un unico criterio. Antes el orden de anime se evaluaba
    // primero y se comia la prioridad del add-on, asi que Nyaa.si (posicion 6
    // de anime) se colaba delante de PenguPlay.
    expect(chips([
      [DEEPFLIX, "DeepFlix", 40],
      [PENGUPLAY, "PenguPlay", 25],
      [TORRENTIO, "Nyaa.si", 30],
      [ANIMES, "AnimeAV1", 5],
    ], true)).toEqual(["AnimeAV1", "PenguPlay", "DeepFlix", "Nyaa.si"]);
  });

  it("lo que instalo el usuario va primero en anime, no solo en pelicula", () => {
    // Un add-on propio que además es fuente de anime ("Nyaa.si" esta en el
    // orden de anime) no puede perder contra un integrado: la regla del
    // usuario es el escalon 0 y no la pisa ninguna otra.
    expect(chips([
      [ANIMES, "AnimeAV1", 5],
      [PENGUPLAY, "PenguPlay", 30],
      [CUSTOM, "Nyaa.si", 1],
    ], true)).toEqual(["Nyaa.si", "AnimeAV1", "PenguPlay"]);
  });

  it("sin anime el orden de add-ons manda sobre el volumen", () => {
    // El caso de la captura: Nyaa.si es de anime pero esto no es anime, asi que
    // DeepFlix debe ir delante aunque Nyaa traiga mas streams.
    expect(chips([
      [TORRENTIO, "Nyaa.si", 194],
      [DEEPFLIX, "DeepFlix", 1],
    ], false)).toEqual(["DeepFlix", "Nyaa.si"]);
  });

  it("los chips en rojo se van al final aunque la escalera los pida delante", () => {
    // El caso de la captura: GojoWtf/Torrentio llegaron vacios, asi que bajan
    // tras lo que trae streams aunque les toque estar por delante.
    expect(chipsWithPending([
      { name: "AnimeAV1", streams: 16 },
      { name: "AnimES", streams: 1 },
      { name: "GojoWtf" },
      { name: "AnimeKai" },
    ], { isAnime: true })).toEqual(["AnimeAV1", "AnimES", "GojoWtf", "AnimeKai"]);
  });

  it("mientras cargan los chips vacios no saltan de sitio", () => {
    // AnimeAV1 esta en el escalon 1 por su nombre, asi que el chip sigue a la
    // cabeza aunque todavia no haya llegado ningun stream de el: esta en
    // Pending. Al terminar la carga y ver que viene vacio se pone en rojo y
    // baja al final. Sin el corte por `loading` se caeria de golpe al montar.
    const entries = [{ name: "AnimeAV1" }, { name: "DeepFlix", streams: 12 }];
    expect(chipsWithPending(entries, { isAnime: true, loading: true })).toEqual(["AnimeAV1", "DeepFlix"]);
    expect(chipsWithPending(entries, { isAnime: true })).toEqual(["DeepFlix", "AnimeAV1"]);
  });

  it("un chip vacio cualquiera ya cae al final por la escalera, sin esperar al fin de la carga", () => {
    // Sin streams no hay add-on dueno, asi que el chip se queda en NO_OWNER: el
    // final de la fila por la escalera, loading o no.
    expect(chipsWithPending([
      { name: "DeepFlix", streams: 12 },
      { name: "AnimeFLV" },
    ], { isAnime: true, loading: true })).toEqual(["DeepFlix", "AnimeFLV"]);
  });
});

describe("sourceFilterPriority", () => {
  it("el id corto de Torrentio vale lo mismo que el completo", () => {
    expect(sourceFilterPriority("torrentio", true)).toBe(sourceFilterPriority(TORRENTIO, true));
  });

  it("cualquier add-on no integrado gana a todos los integrados", () => {
    const custom = sourceFilterPriority(CUSTOM, false);
    expect(custom).toBe(0);
    for (const id of bundledIds) expect(custom).toBeLessThan(sourceFilterPriority(id, true));
  });

  it("un add-on desconocido no recibe el puesto de los del usuario", () => {
    expect(sourceFilterPriority("scraper", undefined)).toBeGreaterThan(0);
  });
});