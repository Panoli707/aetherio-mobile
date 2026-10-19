/**
 * Clasificacion por edad y avisos de contenido del reproductor.
 *
 * Aqui viven dos cosas de naturaleza distinta y por eso conviene no mezclarlas:
 *
 * 1. La EDAD es oficial. La publica cada pais a traves de TMDB
 *    (`content_ratings` en series, `release_dates` en peliculas). No hay nada
 *    que interpretar: si hay un numero, ese es el numero.
 *
 * 2. Los AVISOS son inferidos. TMDB no expone ningun campo de "contenido
 *    sensible"; lo unico disponible son las `keywords`, que son etiquetas
 *    libres escritas por quien fundo la ficha. De ahi sale "Violencia" o
 *    "Lenguaje fuerte".
 *
 * El numero de keywords de TMDB es un proxy de POPULARIDAD, no de intensidad:
 * una pelicula de accion conocida arrastra veinte etiquetas (cinco de ellas de
 * violencia) mientras que una obra de autor puede traer dos y que la violencia
 * sea justo su unico rasgo. Contar coincidencias no es medir. Por eso aqui no
 * se exige un umbral por categoria --seria precision falsa-- y en su lugar
 * solo se pide que TMDB haya dado suficientes datos para que la lista tenga
 * algun sentido.
 *
 * Cuando no se alcanza ese minimo la respuesta es una lista vacia y el
 * reproductor se queda unicamente con la edad oficial. Antes callar que decir
 * algo que no podemos sostener.
 */

import type { StreamQuery } from "../../types/stream";

export interface ContentRating {
  /** Etiqueta oficial lista para pintar, p. ej. "16+" o "12". */
  age: string | null;
  /** Categorias inferidas, ya traducidas y ordenadas. Vacia si no hay datos. */
  advisories: string[];
}

/**
 * Prioridad de pais para elegir clasificacion.
 *
 * Empieza por Espana y no por USA a proposito: las clasificaciones
 * estadounidenses son codigos ("TV-MA", "R", "NR") que no son una edad, y
 * tampoco significan lo mismo fuera de su mercado. Espana/Mexico/Argentina dan
 * directamente el numero que ve el usuario final.
 */
const COUNTRY_PRIORITY = ["ES", "MX", "AR", "CO", "CL", "PE", "BR", "US"] as const;

/** Solo aceptamos una edad con forma de edad. "NR", "UR" o "" no se pintan. */
const AGE_LABEL_RE = /^(\d{1,2})(\+?)$/;

/**
 * Numero minimo de keywords totales para emitir avisos.
 *
 * Con menos de tres, TMDB apenas ha descrito la obra y cualquier lista seria
 * ruido. Es preferible mostrar solo la edad oficial.
 */
const MIN_KEYWORDS = 3;

/** El diseno no cabe en mas lineas, y cinco avisos ya es una accusation. */
const MAX_ADVISORIES = 3;

/**
 * Categorias de aviso y las keywords que las activan.
 *
 * El emparejamiento es exacto sobre el nombre normalizado, nunca por subcadena:
 * "sex" no debe capturar "sexy" ni "sextet". Se eligieron terminos que describen
 * contenido, no generos ni temas, para que el aviso signifique algo concreto.
 */
const CATEGORY_KEYWORDS: ReadonlyArray<{ label: string; keywords: readonly string[] }> = [
  {
    label: "Violencia",
    keywords: ["violence", "violent", "murder", "gore", "shooting", "massacre", "blood"],
  },
  {
    label: "Lenguaje fuerte",
    keywords: ["profanity", "swearing", "explicit language", "vulgarity"],
  },
  {
    label: "Consumo de drogas",
    keywords: ["drugs", "drug addiction", "narcotics", "heroin", "cocaine"],
  },
  { label: "Alcohol", keywords: ["alcohol", "alcoholism", "drunkenness", "drinking"] },
  {
    label: "Contenido sexual",
    keywords: ["sex", "sexuality", "erotica", "erotic", "pornography", "nudity"],
  },
  { label: "Miedo", keywords: ["horror", "scary", "frightening", "slasher", "zombie", "terror"] },
] as const;

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
}

/** "  Violence  " y "VIOLENCE" son la misma keyword. */
function normalizeKeyword(value: unknown): string {
  return typeof value === "string" ? value.trim().toLowerCase().replace(/\s+/g, " ") : "";
}

/** "16", "16+", " 12 " pasan; "", "NR", "UR", "TV-14", "16 anos" no. */
export function normalizeAgeLabel(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const match = AGE_LABEL_RE.exec(value.trim());
  // Se normaliza siempre a "N+" porque el signo no es decorativo: marca que la
  // edad es el minimo, no un rango cerrado. Espana y Mexico escriben "16" a
  // secas y solo Argentina pone "16+", asi que sin esto la misma pelicula
  // cambiaba de formato segun el pais que tocara. Lo que no se admite son las
  // clasificaciones sin edad ("TV-MA", "NR", "UR"): esas no son un numero.
  return match ? `${match[1]}+` : null;
}

/**
 * Clasificacion oficial de un detalle de TMDB.
 *
 * Peliculas y series exponen el dato en sitios distintos: en `release_dates`
 * hay una lista de fechas por pais y la certificacion cuelga de cada una; en
 * `content_ratings` hay una sola por pais. Se recorre la lista en orden de
 * prioridad y se devuelve la primera que tenga una edad con forma de edad.
 */
export function parseAgeRating(detail: unknown, type: string): string | null {
  const root = asRecord(detail);
  if (!root) return null;

  const byCountry = new Map<string, string>();
  if (type === "movie") {
    const results = asRecord(root.release_dates)?.results;
    if (Array.isArray(results)) {
      for (const entry of results) {
        const country = asRecord(entry);
        const iso = normalizeKeyword(country?.iso_3166_1).toUpperCase();
        if (!iso || byCountry.has(iso)) continue;
        const releases = country?.release_dates;
        const list = Array.isArray(releases) ? releases : [];
        // La primera fecha de un pais puede venir sin certificacion; se busca
        // la primera que la tenga en lugar de asumir la posicion 0.
        for (const release of list) {
          const age = normalizeAgeLabel(asRecord(release)?.certification);
          if (age) {
            byCountry.set(iso, age);
            break;
          }
        }
      }
    }
  } else {
    const results = asRecord(root.content_ratings)?.results;
    if (Array.isArray(results)) {
      for (const entry of results) {
        const country = asRecord(entry);
        const iso = normalizeKeyword(country?.iso_3166_1).toUpperCase();
        if (!iso || byCountry.has(iso)) continue;
        const age = normalizeAgeLabel(country?.rating);
        if (age) byCountry.set(iso, age);
      }
    }
  }

  for (const iso of COUNTRY_PRIORITY) {
    const age = byCountry.get(iso);
    if (age) return age;
  }
  // Sin pais de la lista pero con alguna clasificacion numerica suelta, se
  // acepta la primera antes que no mostrar nada.
  for (const age of byCountry.values()) return age;
  return null;
}

/**
 * Categorias de contenido inferidas a partir de las keywords de TMDB.
 *
 * Es una lista de lo que la ficha menciona, no una medicion de intensidad: por
 * eso no se penaliza una categoria que aparece por una sola keyword mientras
 * hay otras que aparecen por varias. El orden refleja cuantos terminos
 * distintos la activaron y el desempate es estable para que el mismo titulo
 * produzca siempre la misma salida.
 */
export function inferAdvisories(keywords: unknown): string[] {
  const results = asRecord(keywords)?.results;
  if (!Array.isArray(results)) return [];

  const names = results
    .map((entry) => normalizeKeyword(asRecord(entry)?.name))
    .filter((name): name is string => name.length > 0);
  if (names.length < MIN_KEYWORDS) return [];

  const seen = new Set(names);
  const scored = CATEGORY_KEYWORDS
    .map((category, order) => ({
      label: category.label,
      order,
      hits: category.keywords.reduce(
        (total, keyword) => (seen.has(keyword) ? total + 1 : total),
        0,
      ),
    }))
    .filter((category) => category.hits > 0)
    .sort((a, b) => (b.hits - a.hits) || (a.order - b.order))
    .slice(0, MAX_ADVISORIES);

  return scored.map((category) => category.label);
}

/** Vista completa a partir del detalle de TMDB, en una sola llamada. */
export function resolveContentRating(detail: unknown, type: string): ContentRating {
  if (!asRecord(detail)) return { age: null, advisories: [] };
  return {
    age: parseAgeRating(detail, type),
    advisories: inferAdvisories(asRecord(detail)?.keywords),
  };
}

/** Identidad del aviso en pantalla: cambia el titulo, cambia lo que se muestra. */
export function buildContentRatingKey(query: StreamQuery | null | undefined): string {
  if (!query?.type || !query.id) return "";
  return [query.type, query.id, query.season ?? "", query.episode ?? ""].join(":");
}

/** Sin edad ni avisos no hay nada que pintar. */
export function hasContentRating(rating: ContentRating | null): boolean {
  return Boolean(rating?.age);
}