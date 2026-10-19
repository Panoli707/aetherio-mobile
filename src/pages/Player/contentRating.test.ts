import { describe, expect, it } from "vitest";
import {
  buildContentRatingKey,
  hasContentRating,
  inferAdvisories,
  normalizeAgeLabel,
  parseAgeRating,
  resolveContentRating,
} from "./contentRating";

function movieDetail(releaseDates: unknown) {
  return { release_dates: { results: releaseDates } };
}

function tvDetail(contentRatings: unknown) {
  return { content_ratings: { results: contentRatings } };
}

function keywordList(...names: unknown[]) {
  // Devuelve el campo `keywords` tal cual llega de TMDB, que es lo que recibe
  // inferAdvisories: el envoltorio lo pone resolveContentRating.
  return { results: names.map((name, id) => ({ id, name })) };
}

describe("normalizeAgeLabel", () => {
  it("acepta edades con y sin signo mas", () => {
    expect(normalizeAgeLabel("16")).toBe("16");
    expect(normalizeAgeLabel("16+")).toBe("16+");
    expect(normalizeAgeLabel(" 12 ")).toBe("12");
    expect(normalizeAgeLabel("7")).toBe("7");
  });

  it("rechaza todo lo que no es una edad", () => {
    for (const value of ["", "  ", "NR", "UR", "TV-MA", "TV-14", "PG-13", "R", "16 anos", "A12", "-16"]) {
      expect(normalizeAgeLabel(value)).toBeNull();
    }
  });

  it("tolera basura no textual", () => {
    expect(normalizeAgeLabel(undefined)).toBeNull();
    expect(normalizeAgeLabel(null)).toBeNull();
    expect(normalizeAgeLabel(16)).toBeNull();
    expect(normalizeAgeLabel({})).toBeNull();
  });
});

describe("parseAgeRating", () => {
  it("prefiere la edad numerica regional sobre el codigo estadounidense", () => {
    const detail = movieDetail([
      { iso_3166_1: "US", release_dates: [{ certification: "R" }] },
      { iso_3166_1: "ES", release_dates: [{ certification: "16" }] },
    ]);
    expect(parseAgeRating(detail, "movie")).toBe("16");
  });

  it("usa Argentina con su signo mas cuando no hay Espana", () => {
    const detail = movieDetail([
      { iso_3166_1: "US", release_dates: [{ certification: "R" }] },
      { iso_3166_1: "AR", release_dates: [{ certification: "16+" }] },
    ]);
    expect(parseAgeRating(detail, "movie")).toBe("16+");
  });

  it("cae en USA solo cuando no hay nada mejor", () => {
    const detail = movieDetail([{ iso_3166_1: "US", release_dates: [{ certification: "13" }] }]);
    expect(parseAgeRating(detail, "movie")).toBe("13");
  });

  it("respeta el orden de prioridad de la lista", () => {
    const detail = movieDetail([
      { iso_3166_1: "BR", release_dates: [{ certification: "18" }] },
      { iso_3166_1: "MX", release_dates: [{ certification: "16" }] },
      { iso_3166_1: "ES", release_dates: [{ certification: "12" }] },
    ]);
    expect(parseAgeRating(detail, "movie")).toBe("12");
  });

  it("salta una certificacion vacia en lugar de asumir la primera fecha", () => {
    const detail = movieDetail([
      { iso_3166_1: "ES", release_dates: [{ certification: "" }, { certification: "16" }] },
    ]);
    expect(parseAgeRating(detail, "movie")).toBe("16");
  });

  it("descarta una clasificacion no numerica y usa la de otro pais", () => {
    const detail = movieDetail([
      { iso_3166_1: "ES", release_dates: [{ certification: "NR" }] },
      { iso_3166_1: "MX", release_dates: [{ certification: "15" }] },
    ]);
    expect(parseAgeRating(detail, "movie")).toBe("15");
  });

  it("acepta cualquier edad numerica cuando no hay pais de la lista", () => {
    const detail = movieDetail([{ iso_3166_1: "JP", release_dates: [{ certification: "15" }] }]);
    expect(parseAgeRating(detail, "movie")).toBe("15");
  });

  it("lee series desde content_ratings", () => {
    const detail = tvDetail([
      { iso_3166_1: "US", rating: "TV-MA" },
      { iso_3166_1: "ES", rating: "16" },
    ]);
    expect(parseAgeRating(detail, "series")).toBe("16");
  });

  it("devuelve null si no hay ninguna edad utilizable", () => {
    expect(parseAgeRating(movieDetail([{ iso_3166_1: "US", release_dates: [{ certification: "NR" }] }]), "movie")).toBeNull();
    expect(parseAgeRating(movieDetail([]), "movie")).toBeNull();
    expect(parseAgeRating({}, "movie")).toBeNull();
    expect(parseAgeRating(null, "movie")).toBeNull();
  });

  it("no cruza campos: content_ratings no sirve para peliculas", () => {
    expect(parseAgeRating(tvDetail([{ iso_3166_1: "ES", rating: "16" }]), "movie")).toBeNull();
  });
});

describe("inferAdvisories", () => {
  it("mapea keywords a categorias en espanol", () => {
    const detail = keywordList("violence", "murder", "profanity", "revenge");
    expect(inferAdvisories(detail)).toEqual(["Violencia", "Lenguaje fuerte"]);
  });

  it("no cuenta dos veces una misma keyword repetida", () => {
    const detail = keywordList("violence", "violence", "profanity");
    expect(inferAdvisories(detail)).toEqual(["Violencia", "Lenguaje fuerte"]);
  });

  it("ordena por terminos distintos que activan la categoria", () => {
    const detail = keywordList("profanity", "swearing", "vulgarity", "violence");
    expect(inferAdvisories(detail)).toEqual(["Lenguaje fuerte", "Violencia"]);
  });

  it("empareja exacto: no captura por subcadena", () => {
    // "sexy" y "sexuality como palabra suelta" no deben activar "sex".
    const detail = keywordList("sexy", "sextet", "bisexual");
    expect(inferAdvisories(detail)).toEqual([]);
  });

  it("normaliza mayusculas y espacios", () => {
    const detail = keywordList("VIOLENCE", "  Explicit   Language  ", "revenge");
    expect(inferAdvisories(detail)).toEqual(["Violencia", "Lenguaje fuerte"]);
  });

  it("se calla con menos de tres keywords", () => {
    expect(inferAdvisories(keywordList("violence", "murder"))).toEqual([]);
    expect(inferAdvisories(keywordList("violence"))).toEqual([]);
    expect(inferAdvisories(keywordList())).toEqual([]);
  });

  it("se calla si hay keywords pero ninguna es de contenido sensible", () => {
    expect(inferAdvisories(keywordList("revenge", "time travel", "based on novel"))).toEqual([]);
  });

  it("nunca devuelve mas de tres categorias", () => {
    const detail = keywordList(
      "violence", "murder", "gore",
      "profanity", "swearing",
      "drugs", "heroin",
      "sex", "nudity",
      "horror", "slasher",
    );
    const result = inferAdvisories(detail);
    expect(result).toHaveLength(3);
    expect(result).toEqual(["Violencia", "Lenguaje fuerte", "Consumo de drogas"]);
  });

  it("tolera payloads rotos sin romperse", () => {
    expect(inferAdvisories(null)).toEqual([]);
    expect(inferAdvisories({})).toEqual([]);
    expect(inferAdvisories({ results: "nope" })).toEqual([]);
    expect(inferAdvisories({ results: 42 })).toEqual([]);
    // Las entradas inservibles ni cuentan como keyword ni rompen el emparejado.
    expect(inferAdvisories(keywordList(1, null, {}, "violence", "profanity", "revenge"))).toEqual([
      "Violencia",
      "Lenguaje fuerte",
    ]);
  });

  it("las keywords inservibles no cuentan para el minimo", () => {
    expect(inferAdvisories(keywordList(1, null, "violence", "profanity"))).toEqual([]);
  });
});

describe("resolveContentRating", () => {
  it("devuelve edad y avisos juntos", () => {
    const detail = {
      ...movieDetail([{ iso_3166_1: "ES", release_dates: [{ certification: "16+" }] }]),
      keywords: keywordList("violence", "profanity", "revenge"),
    };
    expect(resolveContentRating(detail, "movie")).toEqual({
      age: "16+",
      advisories: ["Violencia", "Lenguaje fuerte"],
    });
  });

  it("deja la edad sola cuando no hay keywords suficientes", () => {
    const detail = {
      ...movieDetail([{ iso_3166_1: "ES", release_dates: [{ certification: "12" }] }]),
      keywords: keywordList("revenge", "time travel"),
    };
    expect(resolveContentRating(detail, "movie")).toEqual({ age: "12", advisories: [] });
  });

  it("lee los keywords anidados dentro del detalle", () => {
    const detail = {
      keywords: { results: [{ id: 1, name: "violence" }, { id: 2, name: "revenge" }, { id: 3, name: "murder" }] },
    };
    expect(resolveContentRating(detail, "movie")).toEqual({ age: null, advisories: ["Violencia"] });
  });

  it("devuelve vacio ante cualquier payload inservible", () => {
    expect(resolveContentRating(null, "movie")).toEqual({ age: null, advisories: [] });
    expect(resolveContentRating("nope", "movie")).toEqual({ age: null, advisories: [] });
  });
});

describe("buildContentRatingKey", () => {
  it("incluye temporada y capitulo", () => {
    expect(buildContentRatingKey({ type: "series", id: "tmdb:1399", season: 2, episode: 5 })).toBe(
      "series:tmdb:1399:2:5",
    );
    expect(buildContentRatingKey({ type: "movie", id: "tmdb:550" })).toBe("movie:tmdb:550::");
  });

  it("es vacio sin query", () => {
    expect(buildContentRatingKey(null)).toBe("");
    expect(buildContentRatingKey({ type: "", id: "x" })).toBe("");
  });
});

describe("hasContentRating", () => {
  it("solo la edad oficial hace que haya algo que mostrar", () => {
    expect(hasContentRating({ age: "16+", advisories: [] })).toBe(true);
    expect(hasContentRating({ age: null, advisories: ["Violencia"] })).toBe(false);
    expect(hasContentRating(null)).toBe(false);
  });
});