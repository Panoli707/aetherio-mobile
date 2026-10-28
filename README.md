# Aetherio Mobile

Fork de [trklll/aetherio](https://github.com/trklll/aetherio) para desarrollar una
versión de **Android**. La aplicación original es un gestor multimedia de Windows
con biblioteca de series, películas y anime, reproducción integrada y seguimiento
de progreso.

**Estado: preparación del port. Todavía no hay un APK de este fork.**
El código heredado no es una aplicación Android funcional. Compilar la interfaz
web tampoco demuestra que el reproductor o los comandos nativos funcionen en móvil.

## Documentación

Empieza por la explicación si quieres entender el proyecto; usa la guía cuando
quieras preparar el equipo y ejecutar comprobaciones.

| Documento | Qué explica |
| --- | --- |
| [Arquitectura](docs/android/ARQUITECTURA.md) | Cómo se conectan React, Tauri y Rust; catálogo, perfiles, almacenamiento y reproducción. |
| [Desarrollo paso a paso](docs/android/DESARROLLO.md) | Herramientas para Windows, configuración del SDK, comandos y solución de problemas frecuentes. |
| [Preparador del SDK](scripts/setup-android-sdk.ps1) | Script de instalación de paquetes y configuración de rutas de usuario. Léelo antes de ejecutarlo. |

## Cómo funciona

La interfaz usa **React y TypeScript**. Tauri la muestra en una WebView y permite
llamar a comandos escritos en **Rust** para tareas que requieren acceso nativo.
El catálogo y las fuentes de reproducción son cosas distintas: ver una ficha
no implica que haya un vídeo disponible.

En Windows, el backend reproduce con **libmpv**, cargado desde una DLL. También
incluye un motor torrent y un servidor local de posters con Node. Esas piezas
necesitan adaptación o sustitución antes de funcionar en Android.

Conservaremos Tauri 2 y React como base. **Media3/ExoPlayer es una propuesta** para
la reproducción Android; todavía no está implementado.

## Qué está preparado

- Detección diferenciada de Android, escritorio y navegador.
- Protecciones para no usar varias operaciones de ventanas de PC en Android.
- Actualizador de escritorio limitado al runtime desktop.
- Pantalla de carga integrada para los runtimes que no usan el splash de escritorio.
- Configuración de Vite para el host de desarrollo móvil y la recarga de cambios.
- Dependencias directas WebView2 y Win32 restringidas a Windows.
- Preparador del entorno con JDK 17, Command-line Tools 19.0, API 35, Build Tools 35.0.0 y NDK r28c.

El preparador valida herramientas y paquetes. No genera el proyecto Android,
no compila la aplicación y no acredita que funcione en un dispositivo.

## Próximos hitos

1. Separar el arranque y las dependencias nativas de escritorio del backend móvil.
2. Generar el proyecto Android y obtener un primer APK de navegación y catálogo.
3. Adaptar navegación táctil, tamaños, botón Atrás y persistencia del perfil local.
4. Implementar reproducción Android y validar progreso, audio y subtítulos.
5. Evaluar torrents, almacenamiento, cuentas seguras y comportamiento en segundo plano.

Cada hito debe comprobarse en Android antes de anunciarlo como disponible.
No hay todavía una versión mínima de Android ni compatibilidad de dispositivos
verificada para este fork. iOS queda fuera del alcance actual.

## Desarrollo y pruebas

Los requisitos y comandos completos están en la [guía de desarrollo](docs/android/DESARROLLO.md).
`npm run build` produce archivos web en `dist/`, no un APK.
Las pruebas de TypeScript no sustituyen una compilación Rust ni pruebas en teléfono.

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
