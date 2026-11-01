# Cómo funciona

Aetherio Mobile permite consultar un catálogo en línea y guardar títulos localmente. La versión local actual, **0.3.1**, sigue apareciendo en el teléfono como **Aetherio**; la pública 0.1.0 y la anterior local 0.2.0 aparecen como **Aetherio Native**. Necesita Internet para consultar el catálogo, buscar, cargar fichas e imágenes y reproducir vídeos en línea; no ofrece un catálogo completo sin conexión.

Esta guía recoge el funcionamiento de la **0.3.1 local, todavía sin descarga pública**. Conserva el logo original de escritorio, todas las funciones de proveedores de la 0.2.0 y los avisos de actualizaciones introducidos en la 0.3.0. Añade acceso oficial a TMDB para la información del catálogo cuando la versión instalada tiene configurada una credencial privada. Sin ella, mantiene las alternativas existentes del servicio intermediario (worker) y Cinemeta. TMDB no proporciona enlaces de reproducción. La descarga pública sigue siendo **0.1.0**, `native-v0.1.0-preview.1`, y **no incluye esas novedades ni la búsqueda de fuentes desde las fichas**. En esa descarga, la reproducción se realiza por enlace HTTPS propio o autorizado.

## Inicio y búsqueda

- En **Inicio**, desliza los destacados de la cabecera o usa la flecha para avanzar. Si hay varios, cambian automáticamente cada 8 segundos mientras la pantalla está activa.
- Desliza horizontalmente las secciones, como **Películas en tendencias**, **Series en tendencias** y las selecciones de anime. Su contenido procede de servicios en línea y depende de su disponibilidad.
- Pulsa la flecha del encabezado de una sección para ver sus títulos en una cuadrícula.
- La lupa **Buscar** es un botón separado de las pestañas. Escribe el título de una película o serie y toca un resultado para abrir su ficha.
- Si la navegación se reduce al desplazarte por Inicio, toca **Inicio** para volver arriba y recuperar las otras pestañas.

Si aparece un error de carga, comprueba tu conexión y pulsa **Reintentar** cuando esté disponible.

## Fichas

Toca una portada o un destacado para consultar su sinopsis, valoración, año, géneros y reparto, según la información disponible. En las series puedes elegir una temporada y consultar los episodios reales devueltos por el catálogo, con su número, título, imagen y descripción cuando estén disponibles.

Las temporadas y los episodios son información del catálogo, no enlaces de vídeo. En la versión local 0.3.1, como en la 0.2.0, las fichas utilizan el identificador IMDb del título, cuando está disponible, para consultar los proveedores que hayas añadido. En las series también se usan la temporada y el episodio seleccionados. Que un título aparezca en el catálogo no garantiza que tenga fuentes reproducibles.

## Biblioteca

En una ficha, pulsa **Guardar en biblioteca** para añadir el título a tus favoritos o **Quitar de la biblioteca** para eliminarlo.

Abre **Biblioteca** y utiliza los filtros **Todos**, **Películas** y **Series**. Guardar un título no descarga su vídeo. La biblioteca no es un historial ni una lista de «seguir viendo».

## Ajustes y datos locales

En **Ajustes**, edita **Nombre del perfil** y pulsa **Guardar nombre**. Es un único nombre local: no hay cuentas, perfiles múltiples ni PIN.

El nombre y los favoritos permanecen en este dispositivo, sin sincronización ni importación desde el port anterior. Se eliminan al borrar los datos de la aplicación nativa o desinstalarla.

Las configuraciones de proveedores se guardan cifradas desde la versión local 0.2.0. La lista de Ajustes muestra el nombre y el dominio del proveedor, no su URL completa ni su configuración privada. El reproductor y los mensajes de error tampoco muestran las URL completas de reproducción. No compartas enlaces de configuración que contengan datos privados.

Las credenciales de API son privadas y no se publican con el código ni en esta documentación. El cifrado de las configuraciones de proveedores no protege una credencial incluida en el APK: puede extraerse del archivo compilado. **No compartas ni publiques el APK privado si contiene credenciales**, aunque lo guardes fuera del repositorio.

El nombre, los favoritos y las configuraciones se conservan al actualizar sobre la instalación existente con el mismo paquete `com.panoli707.aetherio.nativeapp`, la misma firma y una versión admitida por Android. Borrar los datos o desinstalar elimina esa información; no hay restauración mediante una cuenta.

## Avisos de actualización

**Introducidos en la versión local 0.3.0 y conservados en la 0.3.1, no disponibles en la pública 0.1.0 ni en la anterior local 0.2.0.** Para empezar a recibirlos desde la 0.1.0 hay que instalar manualmente una versión compatible que los incluya, desde la 0.3.0. La 0.3.1 aún no tiene descarga pública.

Al abrir o volver a Aetherio, la aplicación consulta las versiones publicadas en GitHub en segundo plano, sin bloquear el catálogo. No repite consultas simultáneas ni vuelve a consultar antes de 10 minutos mientras la aplicación siga en ejecución. Si no hay conexión o se agota el tiempo de espera, la consulta termina en silencio y puedes seguir usando la aplicación.

Solo se avisa de una versión con un número de actualización superior al instalado y con información válida y su APK publicado. Los borradores y las versiones iguales o anteriores se ignoran: el nombre de una publicación por sí solo no basta. El aviso solo podrá aparecer cuando una versión nueva esté realmente publicada correctamente; no indica que ya haya actualizaciones en línea.

El diálogo **Actualización disponible** muestra la versión y la etiqueta **Versión preliminar** cuando corresponda.

- **Ver actualización** abre en el navegador la página verificada de esa versión de GitHub. Allí decides si descargas el APK y lo instalas manualmente sobre la aplicación existente para conservar tus datos. Android te pedirá confirmación.
- **Ahora no** cierra el aviso de esa versión hasta un reinicio completo de la aplicación o hasta que se detecte una más nueva. Volver desde otra pantalla o desde Inicio no borra esa decisión.

No hay descarga ni instalación automática, y Aetherio no solicita nuevos permisos para instalar aplicaciones. Consulta [Cómo se instala](INSTALACION.md#actualizaciones-y-datos) antes de actualizar.

## Proveedores y fuentes de reproducción

**Disponibles desde la versión local 0.2.0, también en la 0.3.1, no en la pública 0.1.0.** No hay proveedores de reproducción preinstalados y no necesitas añadirlos para consultar el catálogo o usar la biblioteca.

1. Obtén de un proveedor de confianza la URL HTTPS de su `manifest.json` compatible. Usa solo proveedores y contenidos que tengas permiso para reproducir.
2. Abre **Ajustes**, busca **Proveedores de reproducción** y pega el enlace en **URL HTTPS del manifest.json**. Debe tener una ruta que termine en `/manifest.json`, sin credenciales de usuario en la dirección ni fragmento `#`.
3. Pulsa **Añadir proveedor** y espera la confirmación. Si falla, revisa la conexión, la URL final sin redirecciones y la compatibilidad del manifiesto. Para quitar uno guardado, usa su botón **Eliminar proveedor**.
4. Abre la ficha de una película y pulsa **Buscar fuentes**. En una serie, elige una temporada y toca el episodio que quieras ver; el botón superior **Ver fuentes** corresponde al primer episodio de esa temporada mostrado en la ficha.
5. En la lista de fuentes, deja **Auto** en **Formato de reproducción** o elige **HLS** o **DASH** si conoces el formato. **Auto** respeta el formato indicado por cada fuente. Pulsa **Reproducir** en la opción que quieras abrir.

El proveedor debe ofrecer fuentes de películas o series basadas en IMDb y devolver enlaces de vídeo directos HTTPS. No basta con que su URL termine en `manifest.json`: no todos los proveedores ni todos sus enlaces son compatibles. Solo se admiten fuentes compatibles con el reproductor, sin torrents, DRM, cabeceras privadas, cookies de sesión ni extractores de páginas web. Los nombres, descripciones y datos de calidad proceden del proveedor y no son una verificación de calidad de la aplicación.

Si aparece **Configura tus proveedores**, usa **Ir a Ajustes** para añadir uno. Si no hay fuentes, puede faltar un identificador IMDb compatible, el proveedor puede no cubrir ese título o episodio, o sus enlaces pueden no ser compatibles. Comprueba la conexión y la configuración antes de volver a abrir las fuentes; si un proveedor falla, las fuentes disponibles de los demás se pueden usar.

Para comprobar la integración puedes usar la demo autorizada y optativa de **Big Buck Bunny**, de Blender Foundation (2008), bajo licencia **CC BY 3.0**. La atribución y la [película original](https://peach.blender.org/) figuran también en el ejemplo. La copia de vídeo está alojada en el servicio público de pruebas de ExoPlayer.

1. Añade en Ajustes este manifiesto HTTPS público: `https://raw.githubusercontent.com/Panoli707/aetherio-mobile/main/examples/big-buck-bunny/manifest.json`.
2. Busca **Big Buck Bunny** y abre la ficha correspondiente al IMDb `tt1254207`.
3. Pulsa **Buscar fuentes** y después **Reproducir** en la fuente del ejemplo.

El ejemplo se publica junto con el código fuente, no está preinstalado ni es necesario para utilizar la aplicación. Solo ofrece esta película autorizada; no añade fuentes para el resto del catálogo. Publicar el código y los ejemplos no implica publicar el APK privado ni las credenciales de API.

## Reproducir por enlace

1. Pulsa **Reproducir por enlace** en Ajustes o **Reproducir por enlace propio** en una ficha de 0.3.1, igual que en la local 0.2.0; en la descarga pública 0.1.0, la opción de la ficha se llama **Reproducir por enlace**. Esta opción es independiente de los proveedores: abrirla desde una ficha no obtiene un vídeo de ese título.
2. Introduce en **URL HTTPS del vídeo** una dirección directa de contenido propio o que tengas permiso para reproducir.
3. En **Formato del enlace**, deja **Automático** o selecciona **HLS** o **DASH** si conoces el formato, especialmente si la URL final no termina en `.m3u8` o `.mpd`.
4. Pulsa **Reproducir**. Toca el vídeo para mostrar los controles de pausa y desplazamiento cuando estén disponibles.

Usa la **URL HTTPS final**, no una página web ni un enlace que redirija. La aplicación no admite HTTP, archivos locales, torrents, DRM ni accesos de Netflix u otros servicios que requieran sesión, cookies o cabeceras privadas. No extrae vídeos de páginas web. Estos límites también se aplican a las fuentes de los proveedores desde la versión local 0.2.0.

La reproducción y la posibilidad de avanzar o retroceder dependen del dispositivo, del formato y los códecs, de la red y de la disponibilidad del vídeo. Elegir HLS o DASH no convierte el contenido ni garantiza su compatibilidad.

**Volver** cierra el reproductor y detiene la reproducción. Solo se reproduce en primer plano; no hay servicio de reproducción en segundo plano. Si falla, comprueba la URL final y la conexión antes de pulsar **Reintentar**.

Consulta [Cómo se instala](INSTALACION.md) o [Descarga en Releases](../Releases/README.md).
