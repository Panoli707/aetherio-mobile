# Cómo se instala

## Requisitos

- Un dispositivo con Android 7.0 o posterior.
- Internet para descargar el APK, consultar el catálogo, las fichas y las imágenes y reproducir vídeos en línea.

La descarga pública de Aetherio Mobile sigue siendo **0.1.0**, versión preliminar `native-v0.1.0-preview.1`, y aparece como **Aetherio Native**. La **0.3.1 es local y todavía no tiene descarga pública**: sigue apareciendo como **Aetherio**, conserva el logo original y los proveedores de la anterior local 0.2.0 y los avisos de nuevas versiones introducidos en la 0.3.0. Añade acceso oficial a TMDB para el catálogo cuando tiene configurada una credencial privada; sin ella, mantiene las alternativas del servicio intermediario (worker) y Cinemeta. Ninguna de esas novedades está incluida en el APK público enlazado aquí. La 0.2.0 también se llamaba Aetherio Native; el cambio de nombre en 0.3.0 no cambia el paquete ni la firma.

## Descargar e instalar la versión pública 0.1.0

1. Descarga [AetherioNative-debug.apk desde GitHub Releases](https://github.com/Panoli707/aetherio-mobile/releases/download/native-v0.1.0-preview.1/AetherioNative-debug.apk).
2. Abre el gestor de archivos del dispositivo y entra en **Descargas** o **Downloads**.
3. Toca `AetherioNative-debug.apk`.
4. Si Android lo solicita, permite **Instalar aplicaciones desconocidas** únicamente para ese gestor de archivos. El nombre de la opción puede variar según el dispositivo.
5. Regresa al archivo, pulsa **Instalar** y después **Abrir**.
6. Si lo deseas, revoca al terminar el permiso de instalación del gestor de archivos.

Comprueba que la descarga corresponde al repositorio `Panoli707/aetherio-mobile`. Si el enlace no funciona, consulta la sección [Releases](https://github.com/Panoli707/aetherio-mobile/releases).

No necesitas root, desbloquear el bootloader ni activar la depuración USB. No desactives Play Protect; si Android bloquea el archivo, detén la instalación y comprueba su procedencia.

## Si ya tienes una versión anterior

Si ya tienes **Aetherio Native**, conserva su instalación y sus datos: la 0.3.1 es una actualización de esa misma aplicación, aunque ahora se llame **Aetherio**. Si ya tienes una versión local 0.2.0, 0.3.0 o 0.3.1, el enlace público de 0.1.0 no es una actualización; no desinstales para volver a esa descarga. Si todavía tienes el port híbrido anterior, esta aplicación se instala por separado, sin importar ni borrar sus datos. No necesitas desinstalar el port anterior.

## Instalar la versión local 0.3.1

No hay enlace de descarga pública de la 0.3.1. Si dispones de un APK local de confianza para tu uso privado, pásalo a tu teléfono, ábrelo desde el gestor de archivos y confirma la instalación sobre la aplicación existente, sin desinstalarla. Sigue las mismas precauciones y permisos del apartado anterior. Después aparecerá como **Aetherio**.

Las credenciales de API no se publican con el código. **Si el APK privado contiene credenciales, no lo compartas ni lo publiques: pueden extraerse del archivo compilado, aunque el APK esté fuera del repositorio.** La publicación del código y de los ejemplos no convierte ese APK en una descarga pública ni sustituye al enlace histórico de 0.1.0.

## Actualizaciones y datos

Estos APK utilizan una firma de pruebas local. Una actualización compatible debe mantener el mismo identificador `com.panoli707.aetherio.nativeapp` y la misma firma, además de una versión admitida por Android, para conservar los datos al instalarse sobre la aplicación. Esto también se aplica a las configuraciones de proveedores de las versiones locales desde la 0.2.0.

La pública **0.1.0 no comprueba actualizaciones**. Para disponer del aviso debes instalar manualmente una versión compatible que lo incluya, desde la 0.3.0. La 0.3.1 conserva esta función: al abrir o volver a la aplicación se consultan las versiones publicadas en GitHub sin bloquear el catálogo. Si no hay conexión o la consulta tarda demasiado, no se muestra un error ni se interrumpe el uso.

Solo aparecerá **Actualización disponible** cuando se publique correctamente una versión posterior con su información y APK válidos. No se ofrecen borradores ni versiones iguales o anteriores a la instalada. El aviso muestra la versión y, si corresponde, **Versión preliminar**.

- **Ver actualización** abre en el navegador la página verificada de esa versión en GitHub. Descarga allí el APK y confirma su instalación en Android sobre la aplicación actual para conservar los datos.
- **Ahora no** cierra el aviso para esa versión hasta que cierres por completo y vuelvas a iniciar la aplicación, o hasta que se detecte una versión más nueva. Salir a Inicio y volver no equivale a reiniciarla por completo.

La aplicación no descarga ni instala actualizaciones automáticamente ni solicita nuevos permisos de instalación. Si Android pide autorizar la instalación manual, el permiso corresponde al navegador o gestor de archivos que abra el APK; puedes revocarlo al terminar. Estos avisos no significan que ya haya una actualización publicada.

Si Android informa de un conflicto de firma, **no desinstales para forzar la actualización**. Solicita un APK con la firma compatible.

El nombre y los favoritos se guardan en el dispositivo. Desde la versión local 0.2.0, las configuraciones de proveedores se guardan cifradas y sus URL completas privadas no se muestran en la lista de Ajustes. Esto no protege las credenciales de API incluidas en un APK compilado. Borrar los datos de la aplicación nativa o desinstalarla elimina el nombre, los favoritos y las configuraciones guardadas; no hay sincronización con una cuenta.

La versión local 0.3.1 no incluye proveedores de reproducción preinstalados. Si dispones de ella, puedes añadir un `manifest.json` HTTPS compatible siguiendo [Cómo funciona](FUNCIONAMIENTO.md#proveedores-y-fuentes-de-reproducción), igual que desde la 0.2.0. Allí se explica cómo probar la integración con el ejemplo público y optativo de Big Buck Bunny. No necesitas configurar proveedores para explorar el catálogo o guardar favoritos.

Continúa con [Cómo funciona](FUNCIONAMIENTO.md).
