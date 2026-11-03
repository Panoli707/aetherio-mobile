# Cómo se instala

## Requisitos

- Un dispositivo con **Android 7.0 o posterior**.
- Conexión a Internet para descargar el APK, consultar el catálogo, buscar títulos y reproducir vídeos en línea.

## Descargar e instalar

1. Descarga [**Aetherio.apk desde GitHub**](https://github.com/Panoli707/aetherio-mobile/releases/download/v0.12.0-preview.1/Aetherio.apk), versión preliminar **0.12.0**. Comprueba que corresponde al repositorio `Panoli707/aetherio-mobile`.
2. Abre **Descargas** en el navegador o gestor de archivos del dispositivo y toca `Aetherio.apk`.
3. Si Android lo solicita, permite **Instalar aplicaciones desconocidas** únicamente para el navegador o gestor de archivos que está abriendo el APK. El nombre de la opción puede variar según el dispositivo.
4. Regresa al archivo y pulsa **Instalar**. Cuando termine, pulsa **Abrir**.
5. Si lo deseas, revoca el permiso de instalación del navegador o gestor de archivos al terminar.

La aplicación aparece en el teléfono como **Aetherio**, con el logo original.

No necesitas root ni activar la depuración USB. **No desactives Play Protect**. Si Android bloquea el archivo, detén la instalación y comprueba su procedencia.

## Actualizar sin perder los datos

Si ya tienes **Aetherio** o **Aetherio Native**, abre el APK y confirma la actualización **sobre la instalación existente, sin desinstalarla**. Se mantienen el mismo paquete y firma, de modo que puedes conservar el nombre del perfil, los favoritos y los proveedores guardados.

Si Android informa de un conflicto de firma, detén la instalación y busca un APK de procedencia fiable con firma compatible. **No desinstales ni borres los datos para forzar la actualización**.

Si tienes la versión **0.3.3**, su aviso de actualización seguirá abriendo el navegador. Instala una vez la versión del enlace anterior, siempre sobre la aplicación existente. A partir de ahí, las siguientes actualizaciones pueden descargarse desde la propia aplicación.

1. Abre **Ajustes > Actualizaciones > Buscar actualizaciones** para comprobar si hay una versión disponible, incluidas las versiones preliminares.
2. Pulsa **Actualizar ahora**. Aetherio descarga el APK desde GitHub sin abrir el navegador; puedes consultar el progreso y cancelar la descarga.
3. Al terminar, Aetherio verifica el APK y lo deja listo. Tanto las descargas manuales como las automáticas terminan sin abrir el instalador ni los ajustes del sistema.
4. Pulsa **Instalar actualización** en Aetherio para continuar y confirma la instalación en Android para actualizar sin desinstalar.

En **Android 8 o posterior**, si **Aetherio** aún no tiene permiso para instalar aplicaciones desconocidas, al pulsar **Instalar actualización** se abren los ajustes del sistema para concederlo. Vuelve después a Aetherio y pulsa de nuevo **Instalar actualización**. Si el permiso ya está concedido, se abre directamente el instalador. Este permiso corresponde a Aetherio, no al navegador usado en la primera instalación. **Android siempre requiere que confirmes la instalación**.

El interruptor de actualizaciones automáticas en **Ajustes > Actualizaciones** está **desactivado por defecto** y guarda tu elección. Si lo activas, busca y descarga actualizaciones, **incluidas las versiones preliminares**, al abrir Aetherio, solo en redes no medidas. El APK queda verificado y listo hasta que pulses **Instalar actualización**; no se abren automáticamente el instalador ni los ajustes del sistema. No realiza instalaciones silenciosas ni comprobaciones periódicas con la aplicación cerrada. Una descarga ya iniciada puede continuar mediante el gestor de descargas de Android aunque cierres Aetherio; al volver a abrirla, se recupera su estado.

## Primer uso y datos guardados

Puedes explorar el catálogo, buscar títulos y guardar favoritos sin configurar nada. Provider Hub se registra automáticamente como único resolvedor predeterminado, sin añadir addons. PenguPlay, HdHub, AnimES, DeepFlix y AnimeAV1 permanecen como fuentes adicionales opcionales, apagadas por defecto. Para buscar vídeos, abre una ficha y pulsa **Buscar fuentes**. Los torrents se muestran, pero no se reproducen porque Mobile no tiene motor torrent. La disponibilidad de URLs válidas depende del servicio. Consulta [las limitaciones y comprobaciones del Hub](PROVIDER_HUB.md). También puedes añadir un manifiesto compatible en **Proveedores propios** o usar un enlace HTTPS directo propio o autorizado.

El nombre del perfil, los favoritos, los proveedores, el progreso y los capítulos vistos se guardan en el dispositivo. No hay cuentas ni sincronización: **desinstalar la aplicación o borrar sus datos elimina esa información**.

Continúa con la [guía de funcionamiento](FUNCIONAMIENTO.md).
