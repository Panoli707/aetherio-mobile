import { useEffect, useState } from "react";
import { tmdbFetch } from "../../config/apiKeys";
import type { StreamQuery } from "../../types/stream";
import type { ContentRating } from "./contentRating";
import { buildContentRatingKey, hasContentRating, resolveContentRating } from "./contentRating";
import { resolveTmdbId } from "./utils";

/**
 * Edad oficial y avisos de contenido del titulo en reproduccion.
 *
 * Una sola peticion de detalle trae las dos cosas: la clasificacion vive en
 * `release_dates` (peliculas) o `content_ratings` (series), y los avisos se
 * infieren de `keywords`. Van en el mismo `append_to_response` porque el proxy
 * ya cachea la respuesta completa y partirlo en dos seria el doble de trafico
 * para el mismo dato.
 *
 * Deliberadamente NO se pasa `signal` al fetch. `tmdbFetch` cachea cualquier
 * excepcion de red --incluido un AbortError propio-- como `null` durante 60 s
 * (`requestTmdb` en config/apiKeys). Abortar al cambiar de capitulo dejaria
 * ese titulo sin clasificacion al volver a verlo. Se usa en su lugar el patron
 * `cancelled` que ya sigue el resto del Player; la peticion se completa sola
 * y su resultado es cacheado durante 5 min.
 */
export function useContentRating(query: StreamQuery | null): ContentRating | null {
  const [rating, setRating] = useState<ContentRating | null>(null);
  const requestKey = buildContentRatingKey(query);

  useEffect(() => {
    if (!query) {
      setRating(null);
      return;
    }

    let cancelled = false;
    setRating(null);

    const load = async () => {
      // Las keywords y la clasificacion son del titulo completo. En una serie
      // eso significa la serie, no el capitulo: TMDB no publica keywords por
      // episodio, asi que un capitulo concreto se hereda del conjunto.
      const tmdbId = await resolveTmdbId(query.type, query.id);
      if (cancelled || !tmdbId) return;

      const type = query.type === "movie" ? "movie" : "tv";
      const append = type === "movie"
        ? "release_dates,keywords"
        : "content_ratings,keywords";
      const detail = await tmdbFetch<unknown>(`/${type}/${tmdbId}`, {
        params: { language: "es-ES", append_to_response: append },
      });
      if (cancelled) return;

      const resolved = resolveContentRating(detail, type);
      setRating(hasContentRating(resolved) ? resolved : null);
    };

    void load().catch(() => {
      if (!cancelled) setRating(null);
    });

    return () => {
      cancelled = true;
    };
    // `requestKey` resume type/id/season/episode: cambiar cualquiera de
    // ellos cambia lo que hay que pedir.
  }, [requestKey]);

  return rating;
}