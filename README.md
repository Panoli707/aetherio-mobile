# Aetherio

## Instalación

Necesitas **Android 7.0 o posterior** y conexión a Internet para consultar el catálogo y reproducir vídeos.

1. Descarga [**Aetherio-debug.apk**](https://github.com/Panoli707/aetherio-mobile/releases/download/v0.4.0-preview.1/Aetherio-debug.apk), versión preliminar **0.4.0**.
2. Abre el archivo desde **Descargas**, con tu navegador o gestor de archivos.
3. Si Android lo pide, permite **Instalar aplicaciones desconocidas** solo para la aplicación que abre el APK. No desactives Play Protect.
4. Pulsa **Instalar** y después **Abrir**. Puedes revocar ese permiso al terminar.

La aplicación aparece como **Aetherio**, con el logo original. Si tienes **Aetherio Native**, instala el APK sobre ella, **sin desinstalar**: mantiene el mismo paquete y firma para conservar tus datos. Si Android indica un conflicto de firma, detén la instalación; no borres la aplicación para forzarla.

Consulta la [guía de instalación](docs/INSTALACION.md) para más detalles.

## Funcionamiento

Explora el catálogo de películas y series, busca títulos y abre sus fichas para consultar información, temporadas y episodios. Guarda favoritos en **Biblioteca** y cambia el nombre del perfil en **Ajustes**. El nombre y los favoritos son locales: no hay cuentas, historial de reproducción ni sincronización.

No hay fuentes preinstaladas. Para buscar vídeos desde una ficha, añade en **Ajustes** la URL HTTPS de un `manifest.json` compatible de un proveedor de confianza. Selecciona una fuente y usa **Auto**, **HLS** o **DASH**. También puedes introducir una URL HTTPS directa propia o autorizada mediante **Reproducir por enlace**. No se admiten torrents, DRM, extractores de páginas web ni enlaces que requieran cabeceras privadas o cookies.

En **Ajustes > Actualizaciones > Buscar actualizaciones** puedes comprobar si hay una versión nueva. **Actualizar ahora** descarga el APK desde GitHub dentro de Aetherio, sin abrir el navegador, muestra el progreso y permite cancelar. Al terminar, la aplicación verifica el APK y abre automáticamente el instalador de Android: **debes confirmar la instalación**. En Android 8 o posterior puede ser necesario permitir que Aetherio instale aplicaciones desconocidas; la aplicación abre los ajustes del sistema para conceder ese permiso. Después, vuelve a Aetherio y reintenta la instalación, sin desinstalar.

El interruptor de actualizaciones automáticas está **desactivado por defecto** y conserva tu elección. Si lo activas, Aetherio busca y descarga actualizaciones al abrirse, solo en redes no medidas; no instala sin tu confirmación. El gestor de descargas de Android puede continuar una descarga iniciada aunque cierres Aetherio, que recupera su estado al abrirse de nuevo. No se buscan actualizaciones periódicamente con la aplicación cerrada.

Consulta la [guía de funcionamiento](docs/FUNCIONAMIENTO.md) para configurar proveedores y reproducir vídeos.
