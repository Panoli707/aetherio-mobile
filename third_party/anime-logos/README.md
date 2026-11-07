# Logos de cuatro animes

La ampliación del catálogo de logos se documenta en `docs/artwork/README.md`. Su
registro por recurso, fuente e identidad está en
`app/src/main/resources/anime-logo-catalog.json`; el inventario y los pendientes,
en `docs/artwork/anime-logo-audit.json`. Este apartado conserva la procedencia
original de los cuatro gráficos de Crunchyroll, hoy integrados en el catálogo
con sus nombres estandarizados `anilist_<id>AniList.png`.

Recursos gráficos originales de Crunchyroll, seleccionados a petición del usuario como en sus cuatro capturas. No son obras propias ni están cubiertos por la licencia del código de Aetherio. Los derechos y marcas pertenecen a sus respectivos titulares; la inclusión no implica afiliación.

Se conservaron los colores y la transparencia; solo se recortaron márgenes transparentes y se añadieron cuatro píxeles de margen, sin extraer personajes o controles de las capturas. No se sustituyen portadas ni fondos.

| Recurso | AniList | TMDB | Crunchyroll |
|---|---:|---:|---|
| `anilist_213068.png` | 213068 | 323937 | https://www.crunchyroll.com/series/GT00385792/dandivine |
| `anilist_200294.png` | 200294 | 327765 | https://www.crunchyroll.com/series/GT00384013/super-psychic-policeman-chojo |
| `anilist_179876.png` | 179876 | 306565 | https://www.crunchyroll.com/series/GT00383997/dark-machine-the-animation |
| `anilist_205909.png` | 205909 | 320909 | https://www.crunchyroll.com/series/GT00383999/firefly-wedding |

Origen, consultado el 5 de noviembre de 2026: campo `images_localized.content_logo_polychromatic` de cada ficha pública. URL: `https://imgsrv.crunchyroll.com/cdn-cgi/image/fit=contain,format=png,width=1200/display/keyart/<ID>-content_logo_polychromatic-en-US`, con IDs `T00385792`, `T00384013`, `T00383997`, `T00383999`, respectivamente.

Los recursos finales están en `app/src/main/assets/anime-logos/`. Su asociación es estricta por proveedor, tipo e ID; para TMDB se limita a la primera temporada y se respeta la entrada AniList explícita. Un fallo de carga conserva los logos remotos y el título escrito de respaldo.

SHA-256 de los recursos finales:

```text
c0782c75d401b574e1267fc4df41b2ad1fe6ac2c5766716037fc33974fe2bf10  anilist_200294.png
4499212fd30e4252b73dbb4891acd0f129ebf0b97f62e22625109f2b70abee5a  anilist_213068.png
995438f9312ea226a97280ea56efdaecc919ba2961786fecc9a63320960b87ff  anilist_179876.png
106a100a4dd8bddd3d1a292727f1c08e9a1d7df94252807773d56f0300daaaa7  anilist_205909.png
```
