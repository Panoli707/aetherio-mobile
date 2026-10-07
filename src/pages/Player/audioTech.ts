import type { MediaStream, StreamTechnicalMetadata } from "../../types/stream";
import { languageDisplayName } from "../../utils/languageNames";
import { getStreamFormatBadges } from "../../utils/streamFormatters";

/**
 * Insignia de tecnologia de una pista de audio.
 *
 * mpv llega con el rotulo pegado al titulo ("English - Dolby Atmos 5.1"), y el
 * `.json` de la stream trae `technicalMetadata` con el codec del archivo. La
 * referencia de la barra pide lo contrario: el texto limpio y la etiqueta suelta
 * como insignia (Dolby Atmos) junto al nombre de la seccion.
 *
 * Si hay varias tecnologias en el mismo rotulo gana la de mayor prioridad: en la
 * referencia solo aparece una.
 */
export interface AudioTechBadge {
  id: string;
  label: string;
}

interface TechRule {
  id: string;
  label: string;
  test: RegExp;
}

/**
 * Orden de precedencia: la primera que coincide es la que se muestra.
 *
 * Cada id tiene su PNG en `src/assets/stream-tags/`, que es el mismo badge que
 * pinta el panel de streams. Los ids que no coinciden con el del badge se
 * traducen en `audioBadgeToStreamBadgeId` (abajo).
 */
const TECH_RULES: readonly TechRule[] = [
  { id: "atmos", label: "Dolby Atmos", test: /\batmos\b/i },
  { id: "truehd", label: "Dolby TrueHD", test: /\btrue[\s-]?hd\b/i },
  { id: "dtsx", label: "DTS:X", test: /\bdts[\s._-]?x\b/i },
  { id: "dts-hd-ma", label: "DTS-HD MA", test: /\bdts[\s._-]?hd[\s._-]?ma\b/i },
  { id: "dts-hd", label: "DTS-HD", test: /\bdts[\s._-]?hd\b/i },
  { id: "dts", label: "DTS", test: /\bdts\b/i },
  { id: "ddplus", label: "Dolby Digital Plus", test: /\bdd\+|\be-?ac-?3\b|\bddp\b|\b(?:dd|dolby\s?digital|digital)\s?plus\b/i },
  { id: "dd", label: "Dolby Digital", test: /\bac-?3\b|\bdolby\s?digital\b|\bdd\b/i },
];

/** Codecs con logo propio. Van detras de la tecnologia y antes que los canales. */
const CODEC_RULES: readonly TechRule[] = [
  { id: "a-flac", label: "FLAC", test: /\bflac\b/i },
];

/** Id de la insignia -> id del badge del `.json` (no siempre coinciden). */
const STREAM_BADGE_ID: Record<string, string> = {
  ddplus: "dolby-digital-plus",
  dd: "dolby-digital",
  dtsx: "dts-x",
};

export function audioBadgeToStreamBadgeId(techId: string): string {
  return STREAM_BADGE_ID[techId] ?? techId;
}

/** Disposicion de canales segun el `.json` (mismos ids que los badges del panel). */
function channelsBadge(metadata?: StreamTechnicalMetadata | null): AudioTechBadge | null {
  const channels = metadata?.audioChannels;
  if (channels === 8) return { id: "channels-7.1", label: "7.1 canales" };
  if (channels === 7) return { id: "channels-6.1", label: "6.1 canales" };
  if (channels === 6) return { id: "channels-5.1", label: "5.1 canales" };
  if (channels === 2) return { id: "ch-20", label: "2.0 canales" };
  return null;
}

/**
 * Todos los logos que el `.json` permite poner en la cabecera del menu de
 * audio, en orden de importancia: tecnologia, luego codec y luego canales.
 * Una stream Atmos 5.1 devuelve dos, y los dos tienen PNG.
 */
export function resolveAudioBadges(metadata?: StreamTechnicalMetadata | null): AudioTechBadge[] {
  if (!metadata) return [];
  const tech = audioBadgeFromStream(metadata);
  const codec = CODEC_RULES.find(rule => rule.test.test(metadata.audioCodec ?? ""));
  const channels = channelsBadge(metadata);
  const ordered = [
    tech ? { id: audioBadgeToStreamBadgeId(tech.id), label: tech.label } : null,
    codec ? { id: codec.id, label: codec.label } : null,
    channels,
  ].filter((badge): badge is AudioTechBadge => Boolean(badge));
  return ordered;
}

const CHANNEL_TEST = /\b([2-8])[.]([01])\b/;

/**
 * Quita del rotulo todo lo que describe la tecnologia o los canales y deja solo
 * el idioma/descripcion. Los signos de union sobrantes tambien se limpian, que si
 * no el texto queda "English  -  Commentary".
 */
export function cleanAudioTrackLabel(label: string): string {
  let text = String(label ?? "");
  for (const rule of TECH_RULES) text = text.replace(rule.test, " ");
  text = text
    // "Dolby" solo sobrevive cuando la regla se comio el resto ("Dolby
    // Atmos" -> "Dolby"): la marca no es parte del idioma, asi que cae.
    .replace(/\bdolby\b/gi, " ")
    .replace(/\bdolby\s?vision\b/gi, " ")
    .replace(CHANNEL_TEST, " ")
    // mpv rotula "eng - English": el codigo delante del nombre sobra cuando el
    // nombre ya esta en el idioma de la app. Va DESPUES de quitar la tecnologia:
    // si no, "en - Dolby Atmos 5.1" se queda con la marca pegada.
    .replace(/^\s*[a-z]{2,3}\s*[-–—]\s*(?=[^\s-])/i, "")
    .replace(/\s*[-–—:,]\s*$/g, " ")
    .replace(/^[\s]*[-–—:,]/g, " ")
    .replace(/\s{2,}/g, " ")
    .replace(/\(\s*\)/g, "")
    .replace(/\s+([),])/g, "$1")
    .trim();
  return text || String(label ?? "").trim();
}

/** Tecnologia declarada en el propio rotulo de la pista, si la hay. */
export function audioBadgeFromLabel(label: string): AudioTechBadge | null {
  const source = String(label ?? "");
  for (const rule of TECH_RULES) {
    if (rule.test.test(source)) return { id: rule.id, label: rule.label };
  }
  return null;
}

/**
 * Tecnologia del archivo, segun el `.json` de la stream. Solo se usa si el
 * rotulo de la pista no dice nada: el dato del archivo habla del contenedor
 * entero, la pista manda sobre el.
 */
export function audioBadgeFromStream(metadata?: StreamTechnicalMetadata | null): AudioTechBadge | null {
  if (!metadata) return null;
  // `audioProfile` entra tambien: hay addons que declaran el Atmos ahi en vez de
  // en `dynamicRange`, que es un campo de video (HDR/DV) en la practica.
  const haystack = [metadata.dynamicRange, metadata.audioProfile, metadata.audioCodec, metadata.bitDepth]
    .filter((value): value is string => typeof value === "string" && value.trim().length > 0)
    .join(" ");
  if (haystack) {
    for (const rule of TECH_RULES) {
      if (rule.test.test(haystack)) return { id: rule.id, label: rule.label };
    }
  }
  // Los canales (5.1, 6 canales) no son una tecnologia y no merecen insignia:
  // se quedan en el texto... que tampoco: el rotulo ya va limpio. Sin tecnologia
  // declarada, no hay insignia.
  return null;
}

export interface AudioTrackPresentation {
  /** Texto limpio: solo idioma y descripcion. */
  label: string;
  /** Insignia de tecnologia, si la pista o la stream la declaran. */
  badge: AudioTechBadge | null;
}

export function presentAudioTrack(
  label: string,
  metadata?: StreamTechnicalMetadata | null,
): AudioTrackPresentation {
  const clean = cleanAudioTrackLabel(label);
  return {
    // "en" / "English" / "spa: Comentario" -> "Inglés" / "Inglés" / "Español ·
    // Comentario". El rotulo crudo del stream nunca llega al menu.
    label: languageDisplayName(clean),
    badge: audioBadgeFromLabel(label) ?? audioBadgeFromStream(metadata),
  };
}

/**
 * Orden de los logos de la cabecera del menu de audio: tecnologia, despues
 * codec y despues canales. Son los ids que emite el panel de streams.
 */
const HEADER_BADGE_ORDER: readonly string[] = [
  "atmos",
  "dolby-digital-plus",
  "dolby-digital",
  "truehd",
  "dts-x",
  "dts-hd-ma",
  "dts-hd",
  "dts",
  "a-flac",
  "channels-7.1",
  "channels-6.1",
  "channels-5.1",
  "ch-20",
];

/**
 * Logos de la cabecera del menu de audio del reproductor, ya con su PNG.
 *
 * Usa EXACTAMENTE el detector del panel de streams (`getStreamFormatBadges`),
 * que ya une los patrones del manifiesto sobre el texto de la stream con el
 * `technicalMetadata` del `.json`. Antes la cabecera miraba solo
 * `technicalMetadata`, de modo que una stream que el panel reconocia como
 * Atmos por su nombre o su filename se quedaba sin logo dentro del
 * reproductor: mismo dato, dos detectores, dos respuestas.
 *
 * Sin PNG no se inventa insignia: se filtra por `imageUrl`.
 */
export function resolveHeaderAudioBadges(
  stream: MediaStream | null | undefined,
  limit = 2,
): { id: string; label: string; imageUrl: string }[] {
  if (!stream) return [];
  const badges = getStreamFormatBadges(stream);
  const found: { id: string; label: string; imageUrl: string }[] = [];
  for (const id of HEADER_BADGE_ORDER) {
    const badge = badges.find(entry => entry.id === id && entry.imageUrl);
    if (badge) {
      found.push({ id: badge.id, label: badge.label, imageUrl: badge.imageUrl });
      if (found.length >= limit) break;
    }
  }
  return found;
}