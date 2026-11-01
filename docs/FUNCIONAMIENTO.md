# Cómo funciona

Aetherio Mobile es el port nativo para consultar un catálogo en línea y guardar títulos localmente. La versión actual aparece en el teléfono como **Aetherio Native**. Necesita Internet para consultar las API del catálogo, buscar, cargar fichas e imágenes y reproducir vídeos en línea; no ofrece un catálogo completo sin conexión.

## Inicio y búsqueda

- En **Inicio**, desliza los destacados de la cabecera o usa la flecha para avanzar. Si hay varios, cambian automáticamente cada 8 segundos mientras la pantalla está activa.
- Desliza horizontalmente las secciones, como **Películas en tendencias**, **Series en tendencias** y las selecciones de anime. Su contenido procede de servicios en línea y depende de su disponibilidad.
- Pulsa la flecha del encabezado de una sección para ver sus títulos en una cuadrícula.
- La lupa **Buscar** es un botón separado de las pestañas. Escribe el título de una película o serie y toca un resultado para abrir su ficha.
- Si la navegación se reduce al desplazarte por Inicio, toca **Inicio** para volver arriba y recuperar las otras pestañas.

Si aparece un error de carga, comprueba tu conexión y pulsa **Reintentar** cuando esté disponible.

## Fichas

Toca una portada o un destacado para consultar su sinopsis, valoración, año, géneros y reparto, según la información disponible. En las series puedes elegir una temporada y consultar sus episodios.

Las temporadas y los episodios son información del catálogo, no enlaces de vídeo. **La aplicación no busca fuentes de reproducción automáticamente para los títulos del catálogo.**

## Biblioteca

En una ficha, pulsa **Guardar en biblioteca** para añadir el título a tus favoritos o **Quitar de la biblioteca** para eliminarlo.

Abre **Biblioteca** y utiliza los filtros **Todos**, **Películas** y **Series**. Guardar un título no descarga su vídeo. La biblioteca no es un historial ni una lista de «seguir viendo».

## Ajustes y datos locales

En **Ajustes**, edita **Nombre del perfil** y pulsa **Guardar nombre**. Es un único nombre local: no hay cuentas, perfiles múltiples ni PIN.

El nombre y los favoritos permanecen en este dispositivo, sin sincronización ni importación desde el port anterior. Se eliminan al borrar los datos de la aplicación nativa o desinstalarla.

## Reproducir por enlace

1. Pulsa **Reproducir por enlace** en Ajustes o en una ficha. Abrirlo desde una ficha no obtiene un vídeo de ese título.
2. Introduce en **URL HTTPS del vídeo** una dirección directa de contenido propio o que tengas permiso para reproducir.
3. En **Formato del enlace**, deja **Automático** o selecciona **HLS** o **DASH** si conoces el formato, especialmente si la URL final no termina en `.m3u8` o `.mpd`.
4. Pulsa **Reproducir**. Toca el vídeo para mostrar los controles de pausa y desplazamiento cuando estén disponibles.

Usa la **URL HTTPS final**, no una página web ni un enlace que redirija. No se admiten HTTP, archivos locales, DRM ni accesos de Netflix u otros servicios que requieran sesión, cookies o cabeceras especiales.

La reproducción y la posibilidad de avanzar o retroceder dependen del dispositivo, del formato y los códecs, de la red y de la disponibilidad del vídeo. Elegir HLS o DASH no convierte el contenido ni garantiza su compatibilidad.

**Volver** cierra el reproductor y detiene la reproducción. Solo se reproduce en primer plano; no hay servicio de reproducción en segundo plano. Si falla, comprueba la URL final y la conexión antes de pulsar **Reintentar**.

Consulta [Cómo se instala](INSTALACION.md) o [Descarga en Releases](../Releases/README.md).
