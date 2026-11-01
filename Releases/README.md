# Descargar Aetherio Mobile

**0.1.0 | Versión preliminar | Android 7.0 o posterior**

[**Descargar AetherioNative-debug.apk**](https://github.com/Panoli707/aetherio-mobile/releases/download/native-v0.1.0-preview.1/AetherioNative-debug.apk)

El enlace descarga el APK de `native-v0.1.0-preview.1` desde GitHub Releases, no el código fuente. **Esta sigue siendo la descarga pública actual.**

Incluye catálogo en línea, búsqueda, fichas, favoritos locales y reproducción por URL HTTPS directa propia o autorizada. No busca fuentes del catálogo automáticamente ni ofrece descargas de vídeo, cuentas o sincronización. Se instala como **Aetherio Native**, con el identificador `com.panoli707.aetherio.nativeapp`, sin importar ni borrar los datos del port anterior.

La **0.3.1 es la versión local actual y todavía no tiene descarga pública**. Sigue apareciendo como **Aetherio**, mientras que la pública 0.1.0 y la anterior local 0.2.0 se llaman **Aetherio Native**. Mantiene el mismo paquete y firma para actualizar sobre la instalación existente sin perder los datos.

Conserva el logo original de escritorio y todas las funciones de proveedores de la 0.2.0: proveedores `manifest.json` HTTPS configurables en Ajustes y búsqueda de fuentes desde fichas con IMDb y episodios reales. Mantiene el aviso de actualizaciones publicadas en GitHub introducido en la 0.3.0. Añade acceso oficial a TMDB para la información del catálogo cuando tiene configurada una credencial privada; sin ella, mantiene las alternativas del servicio intermediario (worker) y Cinemeta. TMDB no proporciona fuentes de reproducción. **Ninguna de estas novedades está incluida en el APK público de 0.1.0**. No hay proveedores preinstalados y solo se admiten vídeos directos HTTPS compatibles, sin torrents, DRM, cabeceras privadas ni extractores.

El código fuente y los ejemplos se publican en el repositorio, pero **no se publica el APK privado de 0.3.1 ni las credenciales de API**. Las credenciales incluidas en un APK compilado pueden extraerse, aunque ese archivo esté fuera del repositorio: **no compartas ni publiques el APK privado si las contiene**. El enlace histórico de arriba no ha cambiado. Si ya tienes la 0.2.0, la 0.3.0 o la 0.3.1, ese APK público de 0.1.0 no es una actualización.

Puedes comprobar la integración de proveedores con el [manifiesto HTTPS público y optativo de Big Buck Bunny](https://raw.githubusercontent.com/Panoli707/aetherio-mobile/main/examples/big-buck-bunny/manifest.json), IMDb `tt1254207`. Es una demo autorizada de Blender Foundation (2008), bajo CC BY 3.0, no un proveedor preinstalado. Sigue los pasos de [Cómo funciona](../docs/FUNCIONAMIENTO.md#proveedores-y-fuentes-de-reproducción).

La pública 0.1.0 no comprueba actualizaciones: para disponer del aviso debes instalar manualmente una versión compatible que lo incluya, desde la 0.3.0. La 0.3.1 conserva esta función: solo se avisará cuando una versión posterior esté realmente publicada con información y APK válidos. **Actualización disponible** muestra la versión y, si corresponde, **Versión preliminar**. **Ver actualización** abre su página de GitHub en el navegador para descargar e instalar manualmente; **Ahora no** aplaza el aviso hasta un reinicio completo o una versión más nueva. No hay descarga ni instalación automática ni nuevos permisos de instalación solicitados por la aplicación.

[Cómo se instala](../docs/INSTALACION.md) | [Cómo funciona](../docs/FUNCIONAMIENTO.md)
