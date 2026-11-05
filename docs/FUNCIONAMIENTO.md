# Cómo funciona

Aetherio permite explorar películas y series, guardar favoritos y reproducir vídeos propios o autorizados. Necesitas Internet para consultar el catálogo, cargar imágenes y reproducir vídeos en línea.

## Inicio y búsqueda

Al abrir Aetherio, se muestran el logo y el nombre mientras arranca la interfaz. El catálogo aparece por partes y puede reutilizar metadatos guardados, sin esperar a que respondan todos los servicios.

- En **Inicio**, desliza los destacados de la cabecera. Avanzan automáticamente mientras Inicio está activo; el botón de perfil abre Ajustes.
- Desliza horizontalmente las secciones de películas, series y anime. Pulsa la flecha de una sección para ver sus títulos en una cuadrícula.
- Inicio incorpora las 44 filas predeterminadas de PC, los siete accesos por plataforma y los exploradores de géneros. Los filtros y las diferencias de Android están en [Catálogo de Inicio](CATALOGO_INICIO.md). **Actualizar catálogo**, junto al perfil, fuerza una consulta nueva sin borrar tus datos.
- Toca la lupa **Buscar**, escribe un título y abre uno de los resultados.
- Abre la pestaña **Anime** para ver tus episodios pendientes y los animes que sigues.
- Si la navegación se reduce al desplazarte, toca **Inicio** para volver arriba y recuperar las otras pestañas.

Si aparece un error de carga, comprueba la conexión y pulsa **Reintentar** cuando esté disponible.

Las portadas mantienen la proporción 2:3 y comparten tamaños, márgenes y separación en Inicio, Anime, Biblioteca, búsqueda y relacionados. Las ventanas estrechas usan dos columnas; los teléfonos más anchos, tres; las tabletas adaptan el número al espacio. Las miniaturas de vídeo siguen siendo panorámicas.

## Fichas y episodios

Las fichas de películas, series y anime comparten una estructura con portada grande, título o logo y metadatos. La portada no tiene un botón de reproducción superpuesto: usa **Reproducir** para abrir las fuentes de la película o del episodio indicado; si hay progreso pendiente, el botón conserva el episodio y la posición guardada. Las imágenes de episodios y tráileres tampoco llevan iconos de reproducción superpuestos, pero sus tarjetas siguen siendo pulsables.

La paleta negra, blanca y violeta se aplica a toda la aplicación, incluidos navegación, búsqueda, biblioteca, ajustes, menús y controles del reproductor. El fondo del vídeo sigue siendo negro. Las imágenes principales usan póster en vertical y fondo panorámico en horizontal, solicitando la resolución original de TMDB o la portada grande de AniList cuando existe. La nitidez final depende de la imagen disponible; no se genera una versión HD artificial.

Debajo están las acciones compactas: **Biblioteca / Guardado**, **Seguir / Siguiendo** para anime, **Enlace** para una URL propia e **Información** para consultar cómo funcionan las fuentes. La sinopsis empieza resumida; **Leer sinopsis completa** la despliega y **Ver menos** vuelve a reducirla.

En las series, usa el selector de temporada y desliza horizontalmente las tarjetas de episodios. La portada cambia con la temporada cuando el catálogo ofrece su imagen. Cada tarjeta conserva su menú de visto/no visto y su progreso. La fecha y la duración solo se muestran cuando el catálogo las proporciona. El botón **Volver** permanece visible al desplazarte por la ficha. Ya no se añade la etiqueta «ANIME» sobre la portada.

Las secciones **Tráilers**, **Reparto**, **Acerca de**, **Dónde ver**, **Comentarios** y **Relacionados** aparecen según los datos disponibles. Tráilers y reparto se pueden contraer. Los tráileres se abren en YouTube, no como enlaces de vídeo de Media3. Las reseñas son comentarios públicos de TMDB, no un chat ni un sistema de publicación propio de Aetherio. Toca una tarjeta relacionada para abrir su ficha.

**Dónde ver** muestra ofertas por país de JustWatch a través de TMDB. Puedes cambiar la región; suscripción, alquiler, compra y anuncios se distinguen cuando existen. **Ver opciones en TMDB** abre la página de disponibilidad: no reproduce directamente Netflix ni garantiza que una oferta siga vigente. Si no hay información de una región, se indica sin inventar plataformas.

Que un título o episodio aparezca en el catálogo no garantiza que tenga un vídeo disponible. Puedes buscar fuentes desde su ficha con los servicios integrados o configurar proveedores propios en **Ajustes**.

## Biblioteca y perfil

**Biblioteca** reúne automáticamente las películas y series en curso o vistas, además de tus favoritos y títulos en seguimiento. Puedes usar los filtros **Todos**, **Películas** y **Series**. En una ficha, **Biblioteca / Guardado** cambia solo el favorito: quitarlo no borra el historial. Guardar un título no descarga su vídeo.

En **Ajustes > Perfil local**, edita **Nombre del perfil** y pulsa **Guardar nombre**. Es un nombre local, no una cuenta. No hay perfiles múltiples ni sincronización entre dispositivos.

Ajustes muestra una pantalla de **Configuración**, con buscador y cinco secciones: **Perfil local**, **Fuentes y contenido**, **Reproducción**, **Actualizaciones** y **Acerca de**. Cada sección abre sus controles existentes; **Acerca de** contiene las licencias. El botón Volver y el gesto Atrás regresan al listado sin cambiar las preferencias.

El nombre, los favoritos y los proveedores se guardan en el dispositivo. Se conservan al actualizar sobre la instalación existente, pero **se eliminan al desinstalar o borrar los datos de la aplicación**. No hay restauración mediante una cuenta.

## Continuar viendo y capítulos vistos

Al reproducir una película o un episodio desde sus fuentes, Aetherio guarda localmente la posición y la duración del vídeo cada cinco segundos y al pausar o salir. En **Inicio > Continuar viendo** aparece el último episodio pendiente de cada serie o anime, o la película sin terminar. Pulsa la tarjeta, elige una fuente disponible y el vídeo se reanudará desde la posición guardada. Los enlaces pueden caducar, por eso se vuelven a consultar las fuentes en lugar de guardar sus direcciones.

La ficha recuerda la última temporada que reproduciste. Cada capítulo muestra el minuto y segundo guardados, la duración y una barra de progreso. Toca el icono circular de su miniatura o mantén pulsado el capítulo para abrir el menú inferior de acciones:

- **Marcar como visto / no visto** para el capítulo seleccionado.
- **Marcar anteriores como vistos / no vistos**, dentro de la misma temporada.
- **Marcar temporada como vista / no vista**, solo para capítulos con fecha de emisión confirmada. No se incluyen capítulos futuros ni sin fecha conocida.
- **Reproducir** para elegir una fuente o **Reproducir manualmente** mediante un enlace propio.

Marcar un capítulo manualmente no inventa tiempo de reproducción. Marcarlo como no visto quita su marca y reinicia la posición; si la acción afecta a varios capítulos, se pide confirmación. Los cambios de temporada se guardan juntos y no afectan a otras temporadas. Al llegar al 95 % del vídeo o a su final se marca como visto automáticamente y deja de aparecer en Continuar viendo. Si vuelves a reproducir un capítulo visto, comienza desde el principio.

El progreso y los capítulos vistos se conservan al cerrar o actualizar la app, pero no se sincronizan y se pierden al borrar los datos o desinstalar. No se registran enlaces manuales ni vídeos en directo. Un cierre forzado del proceso puede perder los últimos segundos posteriores al último guardado.

## Anime: pendientes y seguimiento

La pestaña **Anime** se limita a los animes que sigues o reproduces de la temporada de emisión actual. **Continuar viendo** muestra sus episodios pendientes, su posición y una barra de progreso. **Estás viendo** presenta sus portadas y el número de capítulos vistos; el total solo aparece cuando está disponible. Las temporadas anteriores y las completadas siguen en **Biblioteca**, sin borrar el progreso. Usa **Temporada y días de emisión** para descubrir otros títulos.

Los animes con progreso o capítulos marcados se incorporan al seguimiento. También puedes usar **Seguir** desde su ficha sin empezar a reproducir. **Siguiendo** lo retira de esta lista, pero no borra el progreso ni las marcas. Un anime puede permanecer en seguimiento entre episodios o mientras esperas la siguiente emisión.

### Temporada actual y días de emisión

En Inicio, **Anime en emisión** sigue el orden del catálogo de PC. Cuando una tarjeta tiene un horario confirmado, incorpora el último episodio emitido y el total conocido. No se adivina un capítulo a partir de la próxima emisión. Toca una portada para abrir su ficha o la flecha para ir a la temporada y sus días de emisión.

El calendario consulta AniList y exige la temporada y el año actuales, calculados según el calendario de Japón, junto con el estado **en emisión**. No usa una ventana aproximada de fechas de TMDB: una serie de años anteriores no aparece solo por seguir activa. La fila de descubrimiento de Inicio sí puede usar la alternativa TMDB de PC cuando falla AniList; se identifica como alternativa y no inventa horarios ni episodios confirmados.

El calendario empieza en **Lunes**. Puedes elegir **Todos**, otro día o **Sin horario**. Al abrir una ficha y volver, se conservan el filtro elegido y la posición del listado. El día procede de la próxima emisión publicada y se muestra según **Japón (JST)**, no según la hora local ni la disponibilidad de doblaje o subtítulos. Los datos dependen del servicio; la temporada se conserva hasta una hora en memoria y puede volver a consultarse al abrir el calendario. Si falla, se avisa en lugar de sustituirla por series antiguas.

Cuando existen equivalencias explícitas entre AniList y TMDB, las entregas se reúnen en una sola ficha con selector de temporada. Por ejemplo, la boticaria deja de separar la ficha con numeración continua de sus entregas por temporada. Los episodios y el progreso antiguos se traducen con los desplazamientos publicados; se guarda una copia local del historial anterior. La app convierte la numeración para cada proveedor, sin cambiar el capítulo elegido. Las equivalencias ambiguas, incompletas o inexistentes no se fuerzan y nunca se fusiona por parecido del título.

La lista de proveedores muestra su nombre y dominio, no sus URL completas privadas. El reproductor y los mensajes de error tampoco muestran las URL completas de reproducción. Evita compartir enlaces de configuración que contengan datos privados.

## Proveedores y fuentes de reproducción

Desde la versión preliminar 0.17.0, **Aetherio Provider Hub** sincroniza su directorio de addons predeterminados y mantiene el resolvedor integrado: no necesitas añadir manifiestos ni actualizar el APK para incorporar nuevos proveedores administrados compatibles. Los catálogos y metadatos continúan integrados en Mobile. Los adaptadores anteriores permanecen como **Fuentes adicionales (opcionales)**, desactivados por defecto para evitar consultas duplicadas. Sus selecciones antiguas se conservan sin borrarlas; una nueva activación es explícita. **AnimeAV1** sigue siendo nativo e independiente de AnimES. Consulta [contrato, migración y comprobación de Provider Hub](PROVIDER_HUB.md).

Abre una película y pulsa **Reproducir**. Para una serie, elige la temporada y el episodio. Las fuentes aparecen conforme responde cada servicio. Si alguno falla, puedes usar los resultados de los demás. No necesitas copiar sus URLs ni añadir manifiestos para estas fuentes.

**Seleccionar fuente** es un panel translúcido, con fondo desenfocado en Android 12 o posterior. Puedes filtrar por proveedor y tocar la tarjeta completa de una fuente para elegirla; no hay un botón Reproducir dentro de cada tarjeta ni un selector de formato. Se respeta el formato detectado. El botón **Actualizar fuentes**, arriba a la derecha, repite la consulta y permanece visible al desplazar los resultados; se desactiva mientras carga.

AnimeAV1 busca por el título exacto de la entrega sin exigir IMDb. En las fichas unificadas se usa la entrega y el episodio que indica la tabla de equivalencias, no un desplazamiento calculado por el nombre. Admite enlaces directos y los servidores públicos Zilla, MP4Upload y YourUpload cuando puede resolverlos sin ejecutar JavaScript ni iniciar una sesión. Si ya habías guardado tu selección de fuentes antes de añadir AnimeAV1, activa su interruptor en Ajustes.

El motor integrado permite intentar reproducir las fuentes torrent compatibles, conservando índice de archivo y trackers; nunca se envían magnets a Media3 como si fueran MP4. Los scrapers y ejecutores de proveedores JavaScript de PC no están incluidos. Que el Hub devuelva una fuente no garantiza que su URL, formato o codec funcione en el dispositivo. Consulta [las restricciones y el aviso P2P](TORRENT_ENGINE.md).

Para añadir un proveedor propio:

1. Obtén de un proveedor de confianza la URL HTTPS de un `manifest.json` compatible. Usa solo contenido que tengas permiso para reproducir.
2. Abre **Ajustes > Fuentes y contenido > Proveedores propios** y pega el enlace en **URL HTTPS del manifest.json**. Usa la dirección final, sin redirecciones, con una ruta que termine en `/manifest.json`.
3. Pulsa **Añadir proveedor** y espera la confirmación. Para quitarlo después, pulsa **Eliminar proveedor**.
4. Abre la ficha de una película y pulsa **Reproducir**. En una serie, elige la temporada y toca el episodio que quieras ver. El botón principal indica el episodio que abrirá y conserva la posición si hay progreso pendiente.
5. Toca la tarjeta de la fuente elegida. Aetherio usa su formato detectado; no hay que elegir Auto, HLS o DASH en esta pantalla.

No todos los proveedores son compatibles. Los proveedores propios admiten las cabeceras de reproducción Referer, Origin y User-Agent y los metadatos Sec-Fetch limitados que necesita Zilla, pero no sesiones con Cookie o Authorization. Para el Hub se admiten además las cabeceras de proveedor documentadas, incluidas Cookie y Authorization, limitadas al origen del vídeo y eliminadas en HTTP. No se reproduce DRM ni se ejecutan extractores arbitrarios de páginas web. La calidad indicada en una fuente procede del proveedor y no garantiza la calidad del vídeo.

El Hub y las fuentes integradas del PC tienen una excepción limitada para vídeo HTTP en shegu.net y sus subdominios. La tarjeta avisa de ese transporte sin cifrar antes de reproducir. No permite HTTP en otros dominios ni cambia el uso de HTTPS en las APIs, proveedores propios o enlaces manuales.

El Hub no requiere configurar proveedores. Si no hay fuentes, pulsa **Actualizar fuentes** y revisa la conexión: el servicio puede no cubrir ese título o episodio, no tener una correspondencia IMDb válida o no ofrecer enlaces compatibles. Las fuentes opcionales solo se consultan si las has activado expresamente; una caída del Hub no las activa automáticamente.

### Ejemplo opcional: Big Buck Bunny

Puedes reproducir **Big Buck Bunny**, de Blender Foundation, bajo licencia **CC BY 3.0**. Consulta la [película original](https://peach.blender.org/).

1. Añade en **Ajustes > Fuentes y contenido > Proveedores propios** este manifiesto público: `https://raw.githubusercontent.com/Panoli707/aetherio-mobile/main/examples/big-buck-bunny/manifest.json`.
2. Busca **Big Buck Bunny** y abre la ficha correspondiente al IMDb `tt1254207`.
3. Pulsa **Reproducir** y después toca la tarjeta de la fuente del ejemplo.

Este proveedor es opcional, no está preinstalado y solo ofrece esta película.

## Reproducir por enlace

Esta alternativa no necesita proveedores. Abrirla desde una ficha no obtiene automáticamente el vídeo de ese título.

1. Pulsa **Reproducir por enlace** en **Ajustes > Reproducción** o **Reproducir por enlace propio** en una ficha.
2. Introduce en **URL HTTPS del vídeo** una dirección directa de contenido propio o autorizado.
3. En **Formato del enlace**, deja **Automático** o selecciona **HLS** o **DASH** si conoces el formato.
4. Pulsa **Reproducir**. Toca el vídeo para mostrar los controles de pausa y desplazamiento cuando estén disponibles.

Usa una **URL HTTPS de vídeo**, no una página web. Las redirecciones deben permanecer en HTTPS. No se admiten HTTP, archivos locales, torrents, DRM ni enlaces que requieran sesiones privadas. Aetherio no extrae vídeos de páginas web.

La reproducción depende del dispositivo, del formato, de la conexión y de la disponibilidad del vídeo. Elegir HLS o DASH no convierte el contenido ni garantiza su compatibilidad. **Volver** cierra el reproductor y detiene el vídeo; no hay reproducción en segundo plano.

## Controles del reproductor

En teléfonos, el vídeo se abre en horizontal. Toca la pantalla para mostrar u ocultar los controles: pausa, saltos de diez segundos y barra de posición cuando el vídeo permite desplazarse. Un doble toque a la izquierda retrocede y a la derecha avanza.

Los botones inferiores permiten elegir las pistas de audio y subtítulos disponibles, desactivar subtítulos, cambiar la velocidad, ajustar la imagen entre Fit, Fill y Zoom, consultar información y girar la pantalla. La resolución mostrada procede del vídeo que se está reproduciendo. El bloqueo de controles deja disponible el botón para desbloquearlos.

Si abriste el vídeo desde una fuente de una ficha, **Cambiar fuente** vuelve a consultar el mismo título o episodio. También aparece junto a **Reintentar** cuando falla la reproducción. Los enlaces manuales no ofrecen ese cambio de fuente.

En episodios de anime, **Saltar opening** aparece durante el intervalo de introducción cuando IntroDB dispone de tiempos para ese IMDb, temporada y capítulo. Esta consulta pública solo envía esos identificadores, no el enlace del vídeo ni las credenciales. Su disponibilidad y precisión dependen de los datos del servicio. No se adivinan equivalencias entre temporadas de TV y entregas de MyAnimeList.

Si no hay tiempos disponibles, los controles ofrecen **Opening · +90 s** durante los primeros diez minutos, siempre que queden más de 90 segundos. Es un salto manual desde la posición actual, no una detección del opening. Los saltos nunca son automáticos y el bloqueo de controles impide activarlos.

**Saltar ending** aparece durante un intervalo de cierre confirmado. Salta hasta su final, conservando las escenas posteriores cuando existen. Sin tiempos, **Ending · +90 s** permite avanzar manualmente cerca del final, sin fingir que se ha detectado el ending. Los iconos de transporte, menú y barra inferior comparten tamaño y áreas táctiles; la barra inferior usa seis posiciones iguales.

## Actualizaciones

### Buscar e instalar una actualización

1. Abre **Ajustes > Actualizaciones > Buscar actualizaciones** para comprobar las versiones publicadas en GitHub, incluidas las versiones preliminares.
2. Si hay una versión disponible, pulsa **Actualizar ahora** para descargar su APK dentro de Aetherio, sin abrir el navegador. La aplicación muestra el progreso y permite cancelar la descarga.
3. Al terminar una descarga manual o automática, Aetherio verifica el APK y lo deja listo, sin abrir el instalador ni los ajustes del sistema.
4. Pulsa **Instalar actualización** en Aetherio para abrir el instalador y **confirma la instalación en Android** para completar la actualización.

El aviso **Actualización disponible** muestra la versión y, cuando corresponda, **Versión preliminar**.

En **Android 8 o posterior**, si **Aetherio** aún no tiene permiso para instalar aplicaciones desconocidas, al pulsar **Instalar actualización** se abren primero los ajustes del sistema para concederlo. Después, vuelve a la aplicación y pulsa de nuevo **Instalar actualización**. Si el permiso ya está concedido, se abre directamente el instalador. No necesitas desinstalar ni borrar los datos.

### Descargas automáticas y segundo plano

El interruptor de actualizaciones automáticas está en **Ajustes > Actualizaciones**, viene **desactivado por defecto** y conserva tu elección entre sesiones.

Si lo activas, Aetherio busca y descarga actualizaciones, **incluidas las versiones preliminares**, **al abrir la aplicación y solo en redes no medidas**. No hay comprobaciones periódicas mientras está cerrada. La descarga automática deja el APK verificado y listo: no abre el instalador ni los ajustes del sistema. **Debes pulsar Instalar actualización en Aetherio y confirmar después en Android**.

Las descargas se gestionan mediante el gestor de descargas de Android (**DownloadManager**). Una descarga iniciada puede continuar en segundo plano aunque cierres Aetherio; al abrirla de nuevo, la aplicación recupera el estado de la descarga. Puedes consultar su progreso o cancelarla desde Aetherio.

Para conservar los datos, actualiza sobre la aplicación existente, sin desinstalarla. Consulta la [guía de instalación](INSTALACION.md#actualizar-sin-perder-los-datos).
