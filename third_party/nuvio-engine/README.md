# Procedencia y código fuente de Nuvio Engine 0.1.6

## Inventario distribuido

- **Motor:** https://github.com/NuvioMedia/nuvio-engine
- **Revisión fuente:** `167f8541d2e07212a9cf7034b9ca4a0c3b6be4d3` (0.1.6).
- **Fuente completa del motor:** [`source-0.1.6.zip`](source-0.1.6.zip), archivo upstream fijado, incluidos C++, headers, wrapper Kotlin, CMake y scripts.
- **SHA-256 del ZIP:** `826b9aa386e8f777d137bc505968cc926ee458cbca6d0b9d7741f9aa1c81c7af`.
- **AAR de origen:** [`app/libs/lib-nuvio-engine-android-0.1.6.aar`](../../app/libs/lib-nuvio-engine-android-0.1.6.aar).
- **Procedencia del AAR:** NuvioMobile `46febb568348aa5e135bff0d4d5c914933aff5a0`, `composeApp/libs/lib-nuvio-engine-android-0.1.6.aar`.
- **Tamaño:** 16 371 515 bytes.
- **SHA-256 AAR:** `66b049a3625bfdc39c3b5d25a0af9bbc127c530512617e9f57cd988c019d33d6`.
- **Git blob AAR:** `c8d348fce387f4b2bbc4a815f1e1e109fd0f6b89`.
- **Bibliotecas nativas:** `libnuvio_engine.so` para armeabi-v7a, arm64-v8a, x86 y x86_64, sin modificaciones de Aetherio.

## Adaptación de compilación

El wrapper Kotlin de `platform/android/engine/src/main/kotlin` se conserva en [`torrent-engine/src/main/kotlin`](../../torrent-engine/src/main/kotlin) sin cambios de contenido. Se recompila con Kotlin 2.1.21 y JVM 17 junto con Aetherio para evitar la incompatibilidad de metadata Kotlin 2.3 del AAR. No se usa `-Xskip-metadata-version-check`.

El archivo [`torrent-engine/build.gradle.kts`](../../torrent-engine/build.gradle.kts) verifica el AAR y extrae exclusivamente `jni/**` hacia un directorio generado. No utiliza el `classes.jar` original. [`consumer-rules.pro`](../../torrent-engine/consumer-rules.pro) conserva los nombres JNI y constructores de payload originales, añadiendo una regla específica para `NativeFilesPayload`: JNI también construye ese contenedor al listar archivos y R8 no debe eliminar su constructor. El código adaptador/UI de Aetherio está en `app/src/main/java/com/panoli707/aetherio/nativeapp/player/torrent` y sus puntos de integración; no modifica el núcleo C++ ni suplanta la autoría upstream.

Para construir la app y el wrapper: JDK 17 + Android SDK 36, ejecutar `scripts/android.ps1 -Action verifyRelease` desde la raíz. Las instrucciones de configuración y pruebas están en [`docs/TORRENT_ENGINE.md`](../../docs/TORRENT_ENGINE.md).

## Reconstruir el núcleo nativo

Descomprime el ZIP fuente en un directorio de trabajo nuevo. Conserva su contenido y versiones fijadas. Para Android, el proyecto upstream declara NDK `29.0.14206865`, CMake `4.1.2`, C++20 y OpenSSL `3.5.7`. Requiere un host con shell POSIX para `scripts/build-android-openssl.sh`, herramientas de compilación, Java y SDK configurados; no basta con ejecutar esos scripts directamente en PowerShell.

Desde `platform/android`, con `ANDROID_HOME` o `ANDROID_SDK_ROOT` configurado y los componentes instalados:

```sh
./gradlew :engine:assembleRelease
```

El build upstream descarga entradas fijadas y verifica checksums; los detalles y parches a libtorrent están en los scripts/CMake del ZIP. Las versiones declaradas son libtorrent 2.0.12 (commit `740a0b9aeabe00e762cc0efe4a0f27593db2550b`), Boost 1.86.0 y OpenSSL 3.5.7. El archivo `THIRD_PARTY_NOTICES.md` identifica cada licencia y entrada. El APK utiliza las bibliotecas publicadas por upstream, no una afirmación de build bit-a-bit reproducido en este equipo. En particular, la verificación SHA-256 establece la integridad del artefacto fijado, no una auditoría de su toolchain.

## Licencias

Nuvio Engine: **GPL-3.0-or-later**. Los avisos completos extraídos del AAR original están en [`app/src/main/assets/licenses`](../../app/src/main/assets/licenses), accesibles desde Ajustes en el APK. El texto GPL está también en [`LICENSE`](../../LICENSE). Libtorrent/try_signal, Boost y OpenSSL mantienen sus respectivas licencias, incluidas íntegramente junto con los avisos upstream.

El código y los scripts necesarios de la integración se distribuyen en este repositorio; el ZIP fija el fuente upstream incluso si cambia su rama principal. Las credenciales privadas y claves de firma no forman parte del código fuente público. Para usar TMDB autenticado al reconstruir, proporciona tu propio token.
