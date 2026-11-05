# Aetherio

Aetherio es una aplicación multimedia nativa para Android. Gestiona tu biblioteca de series, películas y anime con un reproductor integrado y seguimiento local del progreso. No requiere una cuenta ni ofrece sincronización en la nube.

## Dependencias

- Android 7.0 o posterior.
- Conexión a Internet para catálogos, imágenes y reproducción en línea.
- Espacio libre para la caché al reproducir torrents.

El reproductor y el motor torrent vienen incluidos; no necesitas instalar componentes adicionales.

## Instalación

1. Descarga **Aetherio.apk** desde [Releases](https://github.com/Panoli707/aetherio-mobile/releases). Las versiones actuales son preliminares.
2. Abre el APK y confirma la instalación. Si Android lo solicita, permite instalar aplicaciones desconocidas solo para la aplicación que abre el archivo.
3. Abre Aetherio desde el icono de la aplicación.

Para actualizar, instala el APK sobre la aplicación existente, **sin desinstalar**, para conservar tus datos. Mantén Play Protect activado; si Android bloquea el archivo o indica un conflicto de firma, detén la instalación.

## Actualizaciones

Busca nuevas versiones en **Ajustes > Actualizaciones > Buscar actualizaciones**. Puedes descargarlas desde Aetherio; Android siempre requiere confirmar la instalación.

Las actualizaciones automáticas están desactivadas por defecto. Si las activas, la app comprueba y descarga versiones al abrirse en redes no medidas, incluidas las preliminares. No instala actualizaciones silenciosamente.

## Fuentes y Provider Hub

Provider Hub sincroniza automáticamente los addons predeterminados desde su directorio y mantiene su resolvedor de streams integrado. No necesitas añadir addons para empezar; los nuevos proveedores administrados compatibles no requieren actualizar el APK. Los catálogos, la búsqueda y las fichas usan los servicios integrados de TMDB, AniList y Cinemeta; Inicio añade Jikan/MyAnimeList para sus filas de anime. Consulta [la integración y sus límites](docs/PROVIDER_HUB.md).

Abre una ficha, pulsa **Reproducir** y toca la tarjeta de una fuente. Puedes gestionar fuentes adicionales y proveedores propios en **Ajustes > Fuentes y contenido** o usar una URL HTTPS directa desde **Ajustes > Reproducción**.

La disponibilidad depende de cada proveedor, la red y los códecs del dispositivo. Los torrents requieren confirmación P2P: tu IP puede ser visible y el consumo de datos y almacenamiento puede ser alto. La sesión se cierra al salir o enviar el reproductor a segundo plano. Usa solo contenidos que tengas permiso para reproducir.

## Documentación

- [Instalación y actualización](docs/INSTALACION.md).
- [Funcionamiento de la aplicación](docs/FUNCIONAMIENTO.md).
- [Catálogo de Inicio adaptado de PC: filas, plataformas y géneros](docs/CATALOGO_INICIO.md).
- [Contrato y límites de Provider Hub](docs/PROVIDER_HUB.md).
- [Motor torrent: compilación, pruebas y restricciones](docs/TORRENT_ENGINE.md).
- [Rendimiento del desarrollo y mantenimiento del disco](docs/RENDIMIENTO_Y_MANTENIMIENTO.md).

## Licencia

Aetherio se distribuye bajo **GPL-3.0-or-later**. Consulta [LICENSE](LICENSE) y la [procedencia, código fuente y licencias de las dependencias](third_party/nuvio-engine/README.md). Se conservan los avisos de terceros, también accesibles desde **Ajustes > Acerca de**.
