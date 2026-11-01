# Aetherio Mobile

Aplicación de Aetherio para Android para explorar películas y series, guardar favoritos y reproducir vídeos propios o autorizados mediante una URL HTTPS directa.

Versión local actual: **0.3.1** (código de versión **4**), que sigue apareciendo en el teléfono como **Aetherio**. Conserva el logo original de escritorio y todas las funciones de proveedores de la 0.2.0: puedes añadir un `manifest.json` HTTPS compatible en Ajustes y buscar fuentes de películas y episodios desde sus fichas. No incluye proveedores de reproducción preinstalados.

La descarga pública actual sigue siendo **0.1.0**, versión preliminar `native-v0.1.0-preview.1`, con el archivo histórico `AetherioNative-debug.apk`. **No incluye los proveedores, el logo original ni el aviso de actualizaciones de la versión local. No hay una descarga pública de 0.3.1.** Tanto la pública 0.1.0 como la anterior local 0.2.0 aparecen como **Aetherio Native**.

La 0.3.1 añade acceso oficial a TMDB para la información del catálogo cuando la versión instalada tiene configurada una credencial privada. Sin ella, mantiene las alternativas existentes del servicio intermediario (worker) y Cinemeta. TMDB aporta información de películas y series, no fuentes de reproducción. Las credenciales de API no se publican con el código ni en esta documentación. **Las credenciales incluidas en un APK compilado pueden extraerse: no compartas ni publiques el APK privado si las contiene.**

La 0.3.1 conserva el aviso de actualizaciones introducido en la 0.3.0: consulta las publicaciones de GitHub al abrir o volver a la aplicación, sin bloquear el catálogo. Solo avisará de una versión posterior cuando esté realmente publicada con su información y APK válidos. **Ver actualización** abre la página de esa versión en el navegador para que descargues e instales el APK manualmente; no hay descarga ni instalación automática. La pública 0.1.0 no tiene este aviso: para empezar a usarlo necesitas instalar manualmente una versión compatible que lo incluya, desde la 0.3.0.

El código fuente y los ejemplos se publican en el repositorio; esto no implica publicar el APK privado ni sus credenciales. Para comprobar la integración de proveedores puedes añadir el [manifiesto HTTPS optativo de Big Buck Bunny](https://raw.githubusercontent.com/Panoli707/aetherio-mobile/main/examples/big-buck-bunny/manifest.json) y buscar su ficha, IMDb `tt1254207`. Es una demo autorizada de Blender Foundation (2008), bajo CC BY 3.0, no un proveedor preinstalado. Consulta los pasos en [Cómo funciona](docs/FUNCIONAMIENTO.md#proveedores-y-fuentes-de-reproducción).

Solo se reproducen vídeos directos HTTPS, propios o autorizados. No se admiten torrents, DRM, cabeceras privadas ni extractores de vídeo de páginas web. Las configuraciones de proveedores se guardan cifradas; sus URL completas privadas no se muestran en la lista. El cambio de nombre a Aetherio no cambia la aplicación ni su firma: los datos se conservan al instalar una actualización compatible sobre la existente y se eliminan al desinstalar o borrar los datos.

- [Cómo se instala](docs/INSTALACION.md)
- [Cómo funciona](docs/FUNCIONAMIENTO.md)
- [Descarga del APK en Releases](Releases/README.md)

Concepto original: [Aetherio, de trklll](https://github.com/trklll/aetherio). Esta nueva aplicación también toma como referencia las indicaciones y referencias aportadas por el usuario. Desde la versión local 0.2.0 reutiliza el logo original de escritorio, extraído del ZIP de trklll con permiso; ese permiso no implica una licencia general sobre los demás recursos.
