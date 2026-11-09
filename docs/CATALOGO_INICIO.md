# Catálogo de Inicio: referencia de PC y adaptación Android

La versión 0.15.0 adapta la configuración predeterminada de [Aetherio PC](https://github.com/trklll/aetherio), fijada en la revisión [`53a9276`](https://github.com/trklll/aetherio/tree/53a927670f1f8c34282351f8a6ca62c679f6320e). Las consultas y el orden se implementan de forma nativa en Kotlin; no se incrusta React ni Tauri.

## Filas, en orden

Antes de los catálogos están los destacados, **Continuar viendo** cuando hay progreso local y **Explora por plataforma**. Los títulos se consultan en los servicios: no son una lista congelada dentro del APK.

| Orden | Fila |
| ---: | --- |
| 1 | Series populares |
| 2 | Top Películas |
| 3 | Películas populares |
| 4 | Top Series |
| 5–6 | Netflix: Películas / Series |
| 7–8 | HBO Max: Películas / Series |
| 9–10 | Disney+: Películas / Series |
| 11–12 | Prime Video: Películas / Series |
| 13–14 | Apple TV+: Películas / Series |
| 15 | Las mejores películas de los 90 |
| 16 | Las mejores series de los 90 |
| 17 | Las mejores películas de los 2000 |
| 18 | Películas de terror |
| 19 | Películas familiares |
| 20 | Las mejores series de los 2000 |
| 21 | Series de comedia |
| 22 | Series de drama |
| 23 | Las mejores películas de los 2010 |
| 24 | Películas de ciencia ficción y fantasía |
| 25 | Películas románticas |
| 26 | Las mejores series de los 2010 |
| 27 | Series documentales |
| 28 | Series de acción y aventura |
| 29 | Las mejores películas de los 2020 |
| 30 | Clásicos del cine anteriores a 1990 |
| 31 | Series clásicas anteriores a 1990 |
| 32 | Las mejores series de los 2020 |
| 33 | Anime en emisión |
| 34 | Anime recomendado por la comunidad |
| 35 | Los animes más queridos del momento |
| 36 | Próximos animes a estrenar |
| 37 | Los mejores animes de los 90's |
| 38 | Los reyes del anime |
| 39 | Anime popular |
| 40 | Los mejores animes de los 2000 |
| 41 | Animes en tendencia |
| 42 | Los animes mejor valorados |
| 43 | Los mejores animes de los 2010 |
| 44 | Películas anime más populares |

Las selecciones por décadas usan tarjetas panorámicas; las filas top llevan numeración. **Explorar géneros - Series y películas** se sitúa después de Películas populares y **Explorar géneros - Anime** después de Anime recomendado por la comunidad.

Cada fila admite hasta 20 entradas, como la referencia de PC. En un teléfono se ven menos tarjetas simultáneas, pero se puede deslizar hasta las últimas. Las cuadrículas adaptan sus columnas al ancho sin descartar títulos.

## Plataformas y géneros

Las tarjetas de **Netflix, HBO Max, Crunchyroll, Prime Video, Apple TV+, MUBI y Disney+** abren catálogos nativos. Presentan Top 10 Anime, películas y series populares, Top 10 Películas y series mejor valoradas cuando hay resultados. Crunchyroll consulta hasta diez páginas por región para sus filas generales, con límite de 200 entradas únicas por fila.

Los géneros de cine y series son Acción y aventura, Comedia, Crimen, Documental, Drama, Familia, Misterio y suspenso, Ciencia ficción y fantasía y Western. Se intercalan películas y series de hasta cinco páginas de cada tipo, sin reintroducir anime japonés en este explorador generalista.

Los géneros anime son Acción, Aventura, Comedia, Drama, Fantasía, Horror, Mecha, Música, Psicológico, Romance, Ciencia ficción, Slice of Life, Deportes, Sobrenatural y Suspenso. Se consultan al abrirlos, sin precargar todos sus catálogos al iniciar.

Estos son filtros de descubrimiento y disponibilidad de TMDB, **no acceso a suscripciones ni reproducción de esas plataformas**. La reproducción sigue consultando Provider Hub y las fuentes configuradas. No se activa ninguna fuente adicional automáticamente.

## Consultas y diferencias deliberadas

- Las filas generales usan TMDB en español, conservando endpoints, filtros, umbrales y orden base. Las de plataforma intentan Perú y después Estados Unidos; las filas de Inicio añaden las alternativas por productora o cadena de PC.
- Anime usa consultas de AniList y Jikan/MyAnimeList, con resolución de identidades y las equivalencias explícitas de temporadas existentes. Jikan utiliza el proxy de metadatos predeterminado de PC, sin desplegar servicios. Los registros no resueltos no se publican con IDs inventados. Ante una caída se usa la alternativa TMDB de la fila y se identifica como tal.
- En películas anime, la alternativa consulta películas, no series de televisión. Las temporadas y el historial siguen usando las equivalencias de Mobile; no se fusionan entradas por un título parecido.
- Las filas anime evitan repeticiones entre listas, pero conservan al menos diez entradas disponibles si eliminar duplicados dejaría una fila casi vacía.
- Los destacados tienen selección diaria estable de cine/series y anime, hasta cinco de cada grupo, con controles móviles.
- PC puede reordenar populares y tendencias con JustWatch mediante **SpatialPosters**, una instancia local de escritorio. Mobile no se conecta al `localhost` del PC ni incluye ese servicio: conserva el orden TMDB de base, sin prometer los mismos badges o puestos personalizados.
- No se importan addons, orden manual, filas ocultas ni historial de una instalación particular de PC. Los resultados pueden variar por fecha, región, caché, disponibilidad de APIs y preferencias de escritorio.

La presencia en un catálogo no garantiza un vídeo disponible ni concede derechos de reproducción.

## Carga, caché y errores

Las filas aparecen progresivamente, sin esperar a todos los servicios. Se limitan a cuatro las consultas TMDB simultáneas y se espacian las de Jikan. Las imágenes se cargan al componer tarjetas visibles.

La caché almacena solo metadatos públicos durante seis horas y con un máximo de 16 MiB. Su clave incluye el día y la referencia de catálogo. No guarda enlaces de reproducción ni datos de usuario. Si una fila falla, su título y el resto de los resultados siguen disponibles; la caché válida se conserva.

**Actualizar catálogo**, junto al perfil, fuerza una actualización. Se desactiva durante la carga para evitar peticiones duplicadas. Si falta alguna sección, también se ofrece **Reintentar**.

## Comprobaciones

- `DesktopHomeCatalogTest`: inventario de 44 filas, orden, filtros, regiones, géneros, décadas, tipos, destacados y duplicados.
- `HomeCatalogCacheTest`: caducidad, límites, archivos corruptos y contención de rutas.
- `AetherioRepositoryTest`: carga progresiva, caché, actualización, errores parciales y separación de credenciales entre servicios.
- `DesktopHomeDesignTest`: navegación, filas accesibles, tarjeta número 20, actualización y regreso a la plataforma anterior.
- `DesktopHomeLiveTest`: auditoría opcional de APIs reales y capturas; se activa con `manualDesktopHome=true` en el emulador compartido. No garantiza disponibilidad futura.

Referencias técnicas de PC: `src/hooks/useCatalogs.ts`, `src/services/movieSeriesHomeRows.ts`, `src/services/movieSeriesDecades.ts`, `src/config/homePreferences.ts` y las definiciones de plataformas y géneros de la revisión fijada arriba.
