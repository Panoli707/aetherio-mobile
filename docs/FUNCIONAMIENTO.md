# Cómo funciona

Aetherio permite explorar películas y series, guardar favoritos y reproducir vídeos propios o autorizados. Necesitas Internet para consultar el catálogo, cargar imágenes y reproducir vídeos en línea.

## Inicio y búsqueda

- En **Inicio**, desliza los destacados de la cabecera o usa la flecha para avanzar.
- Desliza horizontalmente las secciones de películas, series y anime. Pulsa la flecha de una sección para ver sus títulos en una cuadrícula.
- Toca la lupa **Buscar**, escribe un título y abre uno de los resultados.
- Si la navegación se reduce al desplazarte, toca **Inicio** para volver arriba y recuperar las otras pestañas.

Si aparece un error de carga, comprueba la conexión y pulsa **Reintentar** cuando esté disponible.

## Fichas y episodios

Toca una portada para consultar la sinopsis, valoración, año, géneros y reparto, según la información disponible. En las series puedes elegir una temporada y consultar sus episodios, con título, imagen y descripción cuando estén disponibles.

Que un título o episodio aparezca en el catálogo no garantiza que tenga un vídeo disponible. Para buscar fuentes desde su ficha necesitas añadir un proveedor compatible en **Ajustes**.

## Biblioteca y perfil

En una ficha, pulsa **Guardar en biblioteca** para añadir el título a tus favoritos o **Quitar de la biblioteca** para eliminarlo. En **Biblioteca** puedes usar los filtros **Todos**, **Películas** y **Series**. Guardar un título no descarga su vídeo.

En **Ajustes**, edita **Nombre del perfil** y pulsa **Guardar nombre**. Es un nombre local, no una cuenta. No hay perfiles múltiples, historial de reproducción, lista de «seguir viendo» ni sincronización entre dispositivos.

El nombre, los favoritos y los proveedores se guardan en el dispositivo. Se conservan al actualizar sobre la instalación existente, pero **se eliminan al desinstalar o borrar los datos de la aplicación**. No hay restauración mediante una cuenta.

La lista de proveedores muestra su nombre y dominio, no sus URL completas privadas. El reproductor y los mensajes de error tampoco muestran las URL completas de reproducción. Evita compartir enlaces de configuración que contengan datos privados.

## Proveedores y fuentes de reproducción

No hay proveedores ni fuentes preinstaladas. No necesitas añadirlos para explorar el catálogo o guardar favoritos.

1. Obtén de un proveedor de confianza la URL HTTPS de un `manifest.json` compatible. Usa solo contenido que tengas permiso para reproducir.
2. Abre **Ajustes**, busca **Proveedores de reproducción** y pega el enlace en **URL HTTPS del manifest.json**. Usa la dirección final, sin redirecciones, con una ruta que termine en `/manifest.json`.
3. Pulsa **Añadir proveedor** y espera la confirmación. Para quitarlo después, pulsa **Eliminar proveedor**.
4. Abre la ficha de una película y pulsa **Buscar fuentes**. En una serie, elige la temporada y toca el episodio que quieras ver. El botón superior **Ver fuentes** corresponde al primer episodio de esa temporada mostrado en la ficha.
5. En **Formato de reproducción**, deja **Auto** o elige **HLS** o **DASH** si conoces el formato. Pulsa **Reproducir** en la fuente elegida.

No todos los proveedores son compatibles: deben ofrecer vídeos directos HTTPS para el título o episodio seleccionado. No se admiten torrents, DRM, extractores de páginas web ni fuentes que necesiten cabeceras privadas o cookies de sesión. La calidad indicada procede del proveedor y no garantiza la calidad del vídeo.

Si aparece **Configura tus proveedores**, pulsa **Ir a Ajustes**. Si no hay fuentes, revisa la conexión y el proveedor: puede no cubrir ese título o episodio, o no ofrecer enlaces compatibles. Puedes usar las fuentes disponibles de otros proveedores aunque uno falle.

### Ejemplo opcional: Big Buck Bunny

Puedes reproducir **Big Buck Bunny**, de Blender Foundation, bajo licencia **CC BY 3.0**. Consulta la [película original](https://peach.blender.org/).

1. Añade en **Ajustes** este manifiesto público: `https://raw.githubusercontent.com/Panoli707/aetherio-mobile/main/examples/big-buck-bunny/manifest.json`.
2. Busca **Big Buck Bunny** y abre la ficha correspondiente al IMDb `tt1254207`.
3. Pulsa **Buscar fuentes** y después **Reproducir** en la fuente del ejemplo.

Este proveedor es opcional, no está preinstalado y solo ofrece esta película.

## Reproducir por enlace

Esta alternativa no necesita proveedores. Abrirla desde una ficha no obtiene automáticamente el vídeo de ese título.

1. Pulsa **Reproducir por enlace** en **Ajustes** o **Reproducir por enlace propio** en una ficha.
2. Introduce en **URL HTTPS del vídeo** una dirección directa de contenido propio o autorizado.
3. En **Formato del enlace**, deja **Automático** o selecciona **HLS** o **DASH** si conoces el formato.
4. Pulsa **Reproducir**. Toca el vídeo para mostrar los controles de pausa y desplazamiento cuando estén disponibles.

Usa la **URL HTTPS final**, no una página web ni un enlace que redirija. No se admiten HTTP, archivos locales, torrents, DRM ni enlaces que requieran sesión, cookies o cabeceras privadas. Aetherio no extrae vídeos de páginas web.

La reproducción depende del dispositivo, del formato, de la conexión y de la disponibilidad del vídeo. Elegir HLS o DASH no convierte el contenido ni garantiza su compatibilidad. **Volver** cierra el reproductor y detiene el vídeo; no hay reproducción en segundo plano.

## Avisos de actualización

Al abrir o volver a Aetherio, se comprueban las actualizaciones publicadas en GitHub sin interrumpir el catálogo. Si no hay conexión, puedes seguir usando las funciones disponibles.

El aviso **Actualización disponible** muestra la versión y, cuando corresponda, **Versión preliminar**.

- **Ver actualización** abre la página de esa versión en GitHub para que decidas si descargas el APK y lo instalas manualmente.
- **Ahora no** aplaza el aviso de esa versión hasta un reinicio completo de la aplicación o hasta que se detecte una más nueva.

No hay descarga ni instalación automática. Para conservar los datos, actualiza sobre la aplicación existente, sin desinstalarla. Consulta la [guía de instalación](INSTALACION.md#actualizar-sin-perder-los-datos).
