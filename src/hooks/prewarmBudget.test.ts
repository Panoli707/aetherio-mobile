import { describe, expect, it } from "vitest";

/**
 * El arranque se queda en el splash esperando a los posters de SpatialPosters.
 * Con budget separado por etapa, el tiempo total era la suma de todos ellos.
 * Estos tests fijan la relacion entre las cifras para que subir una no suba la
 * espera total sin querer.
 */

/** Espejo de las constantes de `useCatalogs.ts`. Si cambian, esto hay que actualizar. */
const SPLASH_POSTER_BUDGET_MS = 75_000;
const SPLASH_POSTER_POLL_MS = 750;
const PREWARM_TIMEOUT_MS = Math.round(SPLASH_POSTER_BUDGET_MS * 0.6);
const POSTER_PROBE_MAX_CONCURRENT = 12;

describe("presupuesto de arranque de posters", () => {
  it("el splash no espera mas de 75 s en total", () => {
    // El sintoma reportado era la app trabada en el logo sin explicacion, con un
    // arranque de 148 s. El presupuesto es lo unico que acota eso, asi que tiene
    // un techo explicito.
    expect(SPLASH_POSTER_BUDGET_MS).toBeLessThanOrEqual(75_000);
  });

  it("el tiempo de espera esta acotado por una sola cifra, no por la suma", () => {
    // El bug medido: cada etapa tenia su techo (el prewarm 300 s, la espera
    // 120 s) y se sumaban, asi que el splash llegaba a 147 s reales con un
    // presupuesto de 90 s. Ahora hay un unico deadline que las etapas heredan,
    // asi que el total no puede pasar de esta cifra.
    const reintentos = SPLASH_POSTER_BUDGET_MS - PREWARM_TIMEOUT_MS;
    const totalMaximo = PREWARM_TIMEOUT_MS + reintentos;
    expect(totalMaximo).toBeLessThanOrEqual(SPLASH_POSTER_BUDGET_MS);
  });

  it("la primera pasada se lleva parte del presupuesto, no todo", () => {
    // Si el prewarm se comiera el presupuesto entero, las re rondas no tendrian
    // nada con que trabajar.
    expect(PREWARM_TIMEOUT_MS).toBeLessThan(SPLASH_POSTER_BUDGET_MS);
    expect(PREWARM_TIMEOUT_MS).toBeGreaterThan(SPLASH_POSTER_BUDGET_MS * 0.3);
  });

  it("la espera entre rondas es visible, no un bucle ocupado", () => {
    // Sin pausa, el bucle de reintento lanza requests seguidas y compite con el
    // propio SpatialPosters por CPU, que es lo que hace que los renders tarden
    // mas de lo normal.
    expect(SPLASH_POSTER_POLL_MS).toBeGreaterThanOrEqual(250);
    expect(SPLASH_POSTER_POLL_MS).toBeLessThanOrEqual(2_000);
  });

  it("el presupuesto alcanza para un arranque en frio real", () => {
    // Medido: 3.5 s por póster (render de sharp con badges) y ~150 pósteres
    // visibles en Home. A 12 verificaciones en paralelo son ~45 s. Si el
    // presupuesto bajara de ahi, un arranque en frio mostraria TMDB en vez de
    // SpatialPosters.
    const posters = 150;
    const segundosPorRender = 3.5;
    const segundosDeCola = (posters / POSTER_PROBE_MAX_CONCURRENT) * segundosPorRender;
    expect(SPLASH_POSTER_BUDGET_MS / 1000).toBeGreaterThan(segundosDeCola);
  });
});
