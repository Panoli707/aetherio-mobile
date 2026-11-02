# Aetherio

## Instalación

Necesitas **Android 7.0 o posterior** y conexión a Internet para consultar el catálogo y reproducir vídeos.

1. Descarga [**Aetherio.apk**](https://github.com/Panoli707/aetherio-mobile/releases/download/v0.5.0-preview.2/Aetherio.apk), versión preliminar **0.5.0**.
2. Abre el archivo desde **Descargas**, con tu navegador o gestor de archivos.
3. Si Android lo pide, permite **Instalar aplicaciones desconocidas** solo para la aplicación que abre el APK. No desactives Play Protect. Si Android bloquea el archivo, detén la instalación; no la fuerces.
4. Pulsa **Instalar** y después **Abrir**. Puedes revocar ese permiso al terminar.

La aplicación aparece como **Aetherio**, con el logo original. Si tienes **Aetherio Native**, instala el APK sobre ella, **sin desinstalar**: mantiene el mismo paquete y firma para conservar tus datos. Si Android indica un conflicto de firma, detén la instalación; no borres la aplicación para forzarla.

Consulta la [guía de instalación](docs/INSTALACION.md) para más detalles.

## Funcionamiento

Explora el catálogo de películas y series, busca títulos y abre sus fichas para consultar información, temporadas y episodios. Guarda favoritos en **Biblioteca** y cambia el nombre del perfil en **Ajustes**. El nombre y los favoritos son locales: no hay cuentas, historial de reproducción ni sincronización.

Las fuentes de PC **PenguPlay, HdHub, AnimES y DeepFlix** están integradas. Puedes activarlas o desactivarlas en **Ajustes > Fuentes de Aetherio PC**. Abre una película o episodio y pulsa **Buscar fuentes**; su disponibilidad depende de cada servicio. Para añadir otro `manifest.json` HTTPS compatible, usa **Proveedores propios**. Selecciona una fuente y deja **Auto** o elige **HLS** o **DASH**. También puedes reproducir una URL HTTPS propia o autorizada mediante **Reproducir por enlace**. No se incluyen los motores de torrents ni de proveedores JavaScript del escritorio, y no se admiten DRM ni sesiones privadas.

Algunas fuentes integradas pueden usar vídeo HTTP sin cifrar en el CDN identificado del PC; la tarjeta lo advierte antes de reproducir. Las APIs y los demás servidores siguen usando HTTPS.

En **Ajustes > Actualizaciones > Buscar actualizaciones** puedes comprobar si hay una versión nueva, incluidas las versiones preliminares. **Actualizar ahora** descarga el APK desde GitHub dentro de Aetherio, sin abrir el navegador, muestra el progreso y permite cancelar. Al terminar una descarga manual o automática, la aplicación verifica el APK y lo deja listo, sin abrir el instalador ni los ajustes del sistema. **Debes pulsar Instalar actualización en Aetherio** para continuar. En Android 8 o posterior, si Aetherio aún no tiene permiso para instalar aplicaciones desconocidas, se abren los ajustes del sistema para concederlo; después, vuelve a Aetherio y pulsa de nuevo **Instalar actualización**. **Confirma la instalación en Android**, sin desinstalar.

El interruptor de actualizaciones automáticas está **desactivado por defecto** y conserva tu elección. Si lo activas, Aetherio busca y descarga actualizaciones, **incluidas las versiones preliminares**, al abrirse, solo en redes no medidas; la instalación sigue requiriendo que pulses **Instalar actualización** y confirmes en Android. El gestor de descargas de Android puede continuar una descarga iniciada aunque cierres Aetherio, que recupera su estado al abrirse de nuevo. No se buscan actualizaciones periódicamente con la aplicación cerrada.

Consulta la [guía de funcionamiento](docs/FUNCIONAMIENTO.md) para configurar proveedores y reproducir vídeos.
