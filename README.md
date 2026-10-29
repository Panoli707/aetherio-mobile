# Aetherio Mobile

Fork de [trklll/aetherio](https://github.com/trklll/aetherio) para desarrollar una
versión de **Android**. La aplicación original es un gestor multimedia de Windows
con biblioteca de series, películas y anime, reproducción integrada y seguimiento
de progreso.

**Estado: primer APK debug de catálogo, con pruebas básicas en emulador.**
Las compilaciones ARM64 (`aarch64`) y x86_64 generan
`src-tauri/gen/android/app/build/outputs/apk/universal/debug/app-universal-debug.apk`.
En un Pixel 7 virtual con API 37 y x86_64 se comprobaron perfiles locales,
catálogos, fichas, guardado en biblioteca y Atrás de Biblioteca a Inicio.
Falta probar en teléfonos físicos ARM64. La reproducción sigue deshabilitada.

## Documentación

Empieza por la explicación si quieres entender el proyecto; usa la guía cuando
quieras preparar el equipo y ejecutar comprobaciones.

| Documento | Qué explica |
| --- | --- |
| [Arquitectura](docs/android/ARQUITECTURA.md) | Cómo se conectan React, Tauri y Rust; catálogo, perfiles, almacenamiento y reproducción. |
| [Desarrollo paso a paso](docs/android/DESARROLLO.md) | Herramientas para Windows, configuración del SDK, comandos y solución de problemas frecuentes. |
| [Preparador del SDK](scripts/setup-android-sdk.ps1) | Script de instalación de paquetes y configuración de rutas de usuario. Léelo antes de ejecutarlo. |
| [Comandos Android](scripts/android.ps1) | Entorno de proceso y acciones reproducibles de compilación y desarrollo. |

## Cómo funciona

La interfaz usa **React y TypeScript**. Tauri la muestra en una WebView y permite
llamar a comandos escritos en **Rust** para tareas que requieren acceso nativo.
El catálogo y las fuentes de reproducción son cosas distintas: ver una ficha
no implica que haya un vídeo disponible.

En Windows, [desktop.rs](src-tauri/src/desktop.rs) reproduce con **libmpv**, cargado
desde una DLL, e incluye torrent y un servidor local de posters con Node.
El arranque Android de [mobile.rs](src-tauri/src/mobile.rs) no incluye esas piezas:
solo habilita opener, caché de texto TMDB y comandos de credenciales que devuelven
error fuera de Windows; `playback_capabilities` declara `backend: "none"`.

Conservaremos Tauri 2 y React como base. **Media3/ExoPlayer es una propuesta** para
la reproducción Android; todavía no está implementado.

## Qué está implementado

- Entrada Rust separada por plataforma y dependencias de escritorio excluidas de Android en Cargo.
- Configuración Android con identificador `com.panoli707.aetherio.mobile`, una vista `main` visible y permisos propios; sin recursos mpv/Node ni updater.
- Proyecto generado en `src-tauri/gen/android`, con Gradle 8.14.3, AGP 8.11.0, `minSdk` 24 y `targetSdk`/`compileSdk` 36.
- Modo Android local: perfiles, Inicio, catálogo, fichas y biblioteca; reproducción deshabilitada con el mensaje «Reproduccion Android pendiente».
- Cuentas y sincronización, proveedores/extractores, yt-dlp, SpatialPosters y Discord deshabilitados en Android. Se conservan imágenes del catálogo, no el servidor de posters de escritorio.
- Base responsive para navegación, perfiles y fichas, con controles de ventana y Big Picture de escritorio desactivados; pendiente de validación táctil real.
- Preparador del entorno con JDK 17, Command-line Tools 19.0, API 36, Build Tools 35.0.0 y NDK `28.2.13676358` (r28c); conserva los paquetes anteriores.
- Script `android.ps1` para `build` y `dev`; `init` está protegido si el proyecto generado ya existe.

El SDK mínimo configurado corresponde a **Android 7.0 / API 24**; es un objetivo,
no una matriz de dispositivos verificada. No hay Media3 implementado.

## Próximos hitos

1. Probar el APK ARM64 en teléfonos físicos y versiones estables de Android.
2. Ampliar pruebas de persistencia, pérdida de red y ciclo de vida del sistema.
3. Ajustar navegación táctil, tamaños, orientación y botón Atrás a partir de esas pruebas.
4. Implementar reproducción Android y validar progreso, audio y subtítulos.
5. Evaluar torrents, almacenamiento, cuentas seguras y comportamiento en segundo plano.

Cada hito debe comprobarse en Android antes de anunciarlo como disponible.
La prueba de emulador no acredita compatibilidad con todos los dispositivos ni
con el mínimo configurado. iOS queda fuera del alcance actual.

## Desarrollo y pruebas

Los requisitos y comandos completos están en la [guía de desarrollo](docs/android/DESARROLLO.md).
`npm run build` produce archivos web en `dist/`, no un APK.
Las pruebas de TypeScript no sustituyen una compilación Rust ni pruebas en teléfono.
Pasaron **588 pruebas de Vitest y 4 de Node**, además de las builds nativas.
La guía detalla qué se comprobó en el emulador y qué sigue pendiente.

Con las herramientas y dependencias preparadas, desde la raíz del repositorio:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\android.ps1 -Action build -Target aarch64
```

El script genera solo APK debug, no una release ni un AAB para Google Play.
`universal` es el nombre de salida: puede incluir más ABI tras compilaciones previas;
no garantiza que el archivo sea exclusivamente ARM64. La guía explica instalación
con `adb`, desarrollo en vivo y firma debug local. No ejecutes `init` al clonar si
`src-tauri/gen/android` ya está presente.

El código y la documentación se mantienen en el repositorio conforme avanza el port;
eso no implica publicar automáticamente binarios. El APK es un artefacto local
ignorado por Git: no lo añadas al repositorio ni publiques keystores o secretos.

El workflow heredado de releases publica **Windows**, no Android. No lo ejecutes
como procedimiento de publicación móvil ni le proporciones las claves del autor original.
Las descargas del [proyecto original](https://github.com/trklll/aetherio/releases)
corresponden a ese proyecto, no a una versión Android de este fork.

## Origen y permisos

El port se desarrolla con permiso del autor original. Se conservan sus avisos y
la [licencia incluida en el instalador de Windows](src-tauri/nsis/LICENSE.txt),
que contiene restricciones de modificación y distribución. El permiso para este
port no se presenta como una licencia abierta ni una autorización general para terceros.

No se añade una licencia nueva al código heredado. Las dependencias conservan sus
propias condiciones. No publiques claves, tokens, archivos de firma ni contenido
multimedia sin autorización.
