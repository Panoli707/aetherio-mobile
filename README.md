# Aetherio

Aetherio es una aplicación nativa para Android con catálogo de películas y series, favoritos locales y reproducción de vídeo desde fuentes compatibles.

## Instalación

Necesitas **Android 7.0 o posterior** y conexión a Internet.

1. Descarga [**Aetherio.apk**](https://github.com/Panoli707/aetherio-mobile/releases/download/v0.13.0-preview.1/Aetherio.apk), versión preliminar **0.13.0**.
2. Abre el archivo desde **Descargas**, con tu navegador o gestor de archivos.
3. Si Android lo pide, permite **Instalar aplicaciones desconocidas** para la aplicación que abre el APK.
4. Pulsa **Instalar** y después **Abrir**. Puedes revocar ese permiso al terminar.

Para actualizar, instala el APK sobre la aplicación existente, **sin desinstalar**, para conservar tus datos. Mantén Play Protect activado; si Android bloquea el archivo o indica un conflicto de firma, detén la instalación.

Consulta la [guía de instalación](docs/INSTALACION.md) para más detalles.

## Reproducción torrent y código fuente

Las fuentes torrent se reproducen con **Nuvio Engine 0.1.6** y Media3, previa confirmación P2P. La disponibilidad depende de los participantes y de los códecs del dispositivo. La sesión se cierra al salir o enviar el reproductor a segundo plano; no es un gestor de descargas.

- [Motor torrent: funcionamiento, compilación y límites](docs/TORRENT_ENGINE.md).
- [Procedencia, fuente fijada y licencias del motor](third_party/nuvio-engine/README.md).
- Aetherio se distribuye bajo **GPL-3.0-or-later**. Consulta [LICENSE](LICENSE); las dependencias conservan sus licencias y avisos propios, también accesibles en Ajustes.
