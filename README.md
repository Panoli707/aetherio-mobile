# Aetherio

## Instalación

Necesitas **Android 7.0 o posterior** y conexión a Internet para consultar el catálogo y reproducir vídeos.

1. Descarga [**Aetherio-debug.apk**](https://github.com/Panoli707/aetherio-mobile/releases/download/v0.3.2-preview.1/Aetherio-debug.apk).
2. Abre el archivo desde **Descargas**, con tu navegador o gestor de archivos.
3. Si Android lo pide, permite **Instalar aplicaciones desconocidas** solo para la aplicación que abre el APK. No desactives Play Protect.
4. Pulsa **Instalar** y después **Abrir**. Puedes revocar ese permiso al terminar.

La aplicación aparece como **Aetherio**, con el logo original. Si tienes **Aetherio Native**, instala el APK sobre ella, **sin desinstalar**: mantiene el mismo paquete y firma para conservar tus datos. Si Android indica un conflicto de firma, detén la instalación; no borres la aplicación para forzarla.

Consulta la [guía de instalación](docs/INSTALACION.md) para más detalles.

## Funcionamiento

Explora el catálogo de películas y series, busca títulos y abre sus fichas para consultar información, temporadas y episodios. Guarda favoritos en **Biblioteca** y cambia el nombre del perfil en **Ajustes**. El nombre y los favoritos son locales: no hay cuentas, historial de reproducción ni sincronización.

No hay fuentes preinstaladas. Para buscar vídeos desde una ficha, añade en **Ajustes** la URL HTTPS de un `manifest.json` compatible de un proveedor de confianza. Selecciona una fuente y usa **Auto**, **HLS** o **DASH**. También puedes introducir una URL HTTPS directa propia o autorizada mediante **Reproducir por enlace**. No se admiten torrents, DRM, extractores de páginas web ni enlaces que requieran cabeceras privadas o cookies.

Al abrir la aplicación se comprueban actualizaciones. El aviso identifica las versiones preliminares: **Ver actualización** abre GitHub y **Ahora no** lo aplaza. No se descargan ni instalan automáticamente.

Consulta la [guía de funcionamiento](docs/FUNCIONAMIENTO.md) para configurar proveedores y reproducir vídeos.
