# Carga de portadas y reproducción de tráileres

## Qué causaba las esperas

La revisión de 0.18.0 encontró trabajo interno en el camino hasta el contenido visible, además de la latencia de los servicios:

- `DetailLoader.open` esperaba los metadatos y después la temporada antes de publicar la ficha. La interfaz ocultaba incluso la imagen disponible en el catálogo.
- `HeroArtwork` prefería descargar el original antes de la variante del catálogo y esperaba al análisis OCR y a posibles alternativas antes de mostrar píxeles.
- Inicio enriquecía todos los destacados después de terminar las 44 filas y publicaba el lote al finalizar el destacado más lento. El indicador global seguía presente aunque ya hubiera filas disponibles.

Provider Hub y sus proveedores de vídeo no intervienen en la descarga de esas portadas. Los metadatos proceden de TMDB, AniList y Cinemeta; parte de las filas anime utiliza Jikan. Las imágenes se descargan de los servidores que anuncia el catálogo. Una caída o lentitud de estos servicios sigue pudiendo retrasar contenido: esta revisión no demuestra la causa de cada espera observada en otro teléfono o conexión.

## Comportamiento desde 0.19.0

- La portada disponible se intenta cargar al entrar en la ficha. Los metadatos se publican sin esperar los episodios. Si falla la temporada, la ficha no desaparece y el reintento puede dirigirse solo a ella.
- Durante la carga inicial puede verse la imagen general antes de la específica de la temporada. Los cambios de temporada explícitos siguen manteniendo la portada anterior hasta disponer de la nueva imagen y sus episodios; no se habilitan episodios de una temporada pendiente.
- La primera imagen válida se muestra sin esperar al OCR. Se prueba antes la variante del catálogo, reutilizable desde la caché de imágenes. Los originales de mayor resolución y las alternativas son mejoras posteriores; se mantiene una única portada visible.
- Sin una ilustración alternativa real, el OCR se omite. La búsqueda opcional basada en OCR tiene un presupuesto cooperativo de cinco segundos, y las peticiones de refinamiento de imagen tienen dos segundos cada una. La inicialización del motor nativo no proporciona una garantía de interrupción dura, pero no retiene la primera imagen visible.
- Las filas iniciales y las dos filas anime que alimentan el carrusel tienen prioridad sobre las editoriales. Se conservan el orden del catálogo, todas sus filas y el límite de cuatro operaciones simultáneas. Los detalles de destacados se enriquecen cuando las filas iniciales están listas y se publican individualmente.
- Cada fila conserva sus estados de carga/error. Con contenido disponible ya no aparece el mensaje global «Cargando catálogo…». La actualización completa conserva su estado independiente para evitar actualizaciones duplicadas.

La separación de metadatos y petición de temporada afecta a los catálogos que hacen esas consultas por separado, especialmente TMDB. AniList y Cinemeta pueden incluir episodios en su propia respuesta de ficha; esa respuesta sigue dependiendo de su servicio. Incluso en ese caso se intenta mostrar la portada del catálogo sin esperar la ficha. No se recortan calendarios paginados ni se inventan episodios para aparentar una carga terminada.

## Evidencia y límites de las mediciones

Una prueba JVM con reloj virtual introduce 100 ms de latencia en metadatos y 5.000 ms adicionales en la temporada. Antes, el flujo secuencial solo publicaba la ficha a los 5.100 ms. La prueba de la nueva implementación exige metadatos utilizables a los 100 ms y episodios a los 5.100 ms. Esto verifica que se elimina una dependencia; **no es una medición de velocidad de Internet ni una promesa de mejora porcentual en un teléfono**.

Las pruebas Android retienen por separado una imagen alternativa y el OCR mediante compuertas. Exigen píxeles de la primera portada y ausencia de su indicador de carga mientras sigue pendiente el trabajo opcional. Se mantienen pruebas de orientación, proporciones, respuestas antiguas y selección de temporadas.

Como comprobación externa se hicieron tres GET por recurso desde el equipo de desarrollo, con procesos independientes de curl y sin caché local de la app. Todos respondieron HTTP 200. Medianas de tiempo total observadas:

| Recurso público | Mediana | Bytes por respuesta |
| --- | ---: | ---: |
| Ficha de Cinemeta | 402 ms | 2.541 |
| Metadatos TMDB mediante el proxy existente | 253 ms | 1.633 |
| Póster TMDB w500 | 526 ms | 70.258 |
| El mismo póster original | 825 ms | 938.879 |

Son muestras pequeñas del 5 de noviembre de 2026, no percentiles de producción. El original transfería unas 13,4 veces más bytes en ese ejemplo. La consulta de referencia no incluye los recursos `append_to_response` de la ficha completa. Las mediciones del proxy no representan la API directa autenticada de TMDB que utiliza un APK con token. No son tiempos de carga completos de Aetherio ni permiten extrapolar tiempos al teléfono del usuario.

## Diagnóstico local para desarrollo

Las compilaciones debug escriben registros `AetherioLoad` en logcat. Release no instala el observador de registros y no envía telemetría. Las etiquetas son enumeraciones cerradas: no se registran títulos, IDs, direcciones completas, consultas, cabeceras ni credenciales.

Se separan DNS, conexión, espera de cabeceras, cuerpo de respuesta, cola de catálogo, parseo JSON, decodificación de imágenes, procedencia de caché de memoria/disco/red, conversión de bitmap, OCR y primera imagen preparada para dibujar. Esta última marca señala la publicación del estado, no garantiza que el compositor haya presentado ese fotograma. Las muestras de caché son contadores de procedencia, no tiempos de carga. Las categorías de servicio reconocen TMDB, AniList, Jikan, Cinemeta e imágenes conocidas; el resto se agrupa como `OTHER`.

Para comparar versiones, usar el mismo dispositivo y conexión, registrar primer contenido y operaciones completas por separado, repetir cargas con y sin caché disponible y no borrar datos personales para generar una carga fría. No confundir un emulador con el compositor saturado con latencia de un proveedor. La prueba existente `ScrollPerformanceInstrumentation` puede medir fotogramas en un APK optimizado; no sustituye la medición de red ni un ensayo en teléfono físico.

## Qué tráileres pueden usar el reproductor de Aetherio

`TrailerPlayback` elige una actividad interna según la clase de recurso:

- **Enlace directo HTTPS:** abre `VideoActivity`, el mismo reproductor Media3 de Aetherio, sin sesión torrent, cambio de fuentes ni progreso de episodios. Se admiten archivos MP4/M4V/WebM/MOV y manifiestos HLS/DASH explícitos de `trailerStreams` en metadatos Cinemeta. Se valida el enlace antes de abrirlo; su existencia no garantiza formato, disponibilidad ni derechos de reproducción. Utilizar solo contenido propio o autorizado.
- **ID de YouTube:** conserva `TrailerActivity` y el IFrame oficial dentro de la app, con marca y controles de YouTube. No abre automáticamente otra app. TMDB y AniList entregan estos IDs, no enlaces MP4/HLS. No se extraen streams, eluden restricciones ni se consultan proveedores de películas para un tráiler.

Por tanto, el tráiler de YouTube de la captura del usuario **seguirá usando el reproductor oficial integrado** si el catálogo no ofrece una alternativa directa autorizada. Esta versión no promete convertir todos los tráileres de YouTube al reproductor nativo. La tarjeta distingue «Reproductor de Aetherio» de «YouTube · Reproductor oficial integrado».
