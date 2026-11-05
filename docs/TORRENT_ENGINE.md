# Motor torrent de Aetherio

## Reproducir una fuente

1. Abre una ficha y busca sus fuentes.
2. Toca la tarjeta de una fuente torrent para abrir directamente el reproductor, sin un aviso P2P intermedio. Usa contenido que tengas permiso para reproducir.
3. Espera los metadatos. Si el proveedor no identifica un archivo y el torrent contiene varios vídeos, selecciona el episodio/archivo.
4. Usa los controles habituales. **Cambiar fuente** vuelve a la búsqueda; **Reintentar** crea una sesión nueva.

Tu IP puede ser visible para otros participantes. La subida de contenido está configurada como desactivada, pero esto no anonimiza las conexiones P2P ni elimina el tráfico de protocolo. Considera el consumo de datos de tu conexión.

Al salir o enviar la actividad a segundo plano se cierra el motor. Al regresar se prepara una ruta nueva y se recupera la posición según el historial. No hay descargas permanentes, seeding en segundo plano ni servicio adicional. La rotación normal mantiene la actividad y su sesión.

## Referencia de la integración 0.13.0

| Aspecto | Configuración |
|---|---|
| Motor | Nuvio Engine 0.1.6, libtorrent 2.0.12 |
| Reproductor | Media3 1.5.1 oficial |
| Solicitudes | Hash v1 de 40 caracteres hex o 32 Base32; magnet BTIH |
| Archivo | Índice explícito validado; pista de nombre; vídeo único; selector si hay ambigüedad |
| Trackers | `tr` de magnet y `sources` con prefijo `tracker:`; DHT lo administra el motor |
| Perfil/memoria | Balanced, 64 MiB |
| Caché | Objetivo de hasta 2 GiB, reducido según espacio libre, en caché privada de Android |
| Reserva | 256 MiB libres; se exige además un mínimo operativo de 64 MiB al iniciar |
| Subida | `NuvioUploadMode.Disabled` |
| Metadatos / preparación | 90 segundos / 30 segundos; la selección manual no consume ese plazo |
| HTTP local | Conexión 5 segundos, lectura 45 segundos, sin redirecciones ni proxy |
| Datos estancados | Error recuperable después de 120 segundos sin avance durante buffering activo |
| Transporte interno | `http://127.0.0.1:<puerto>/stream/<token>` exacto, revocable por sesión |

El límite de caché no es un techo físico estricto: el motor puede proteger bloques en uso y superar el presupuesto. Aetherio comprueba el espacio libre durante la sesión y detiene la reproducción si se invade la reserva. Para recuperar espacio fuera de reproducción, usa **Ajustes de Android > Aplicaciones > Aetherio > Almacenamiento > Borrar caché**, no **Borrar datos**.

Los MKV/MP4 y otras extensiones de vídeo son candidatos de selección, no una garantía de decodificación. DTS/TrueHD, HDR, perfiles de vídeo o subtítulos ASS complejos dependen de Media3 y del dispositivo. No hay transcodificación. El selector de fuentes respeta el formato HTTP detectado; los torrents se reproducen como archivo progresivo, nunca como HLS/DASH forzado.

No se admiten nuevos torrents BTMH/v2, archivos `.torrent` de usuario o parámetros magnet arbitrarios de webseed. Los trackers se validan sin resolver DNS en la interfaz: se rechazan esquemas desconocidos, credenciales, controles y destinos numéricos privados/locales. Los nombres de tracker se resuelven después en el motor nativo; esto no constituye una garantía contra DNS rebinding ni una sandbox de destinos de todos los peers DHT.

## Arquitectura y seguridad

- `TorrentRequestParser` normaliza hashes y crea un magnet canónico, sin transportar respuestas completas ni credenciales.
- `TorrentSessionManager` serializa un motor por sesión. Cancelar una espera Kotlin no cancela necesariamente su operación nativa: el bloque de limpieza destruye siempre el motor antes de permitir el siguiente.
- Un fallo de cierre bloquea nuevas sesiones en el proceso hasta reiniciar la app; no se reutilizan directorios con un motor de cierre incierto.
- `TorrentRoute` es una capacidad revocable creada tras `prepareStream`, no una URL aceptada desde un Intent externo.
- `TorrentDataSourceFactory` separa vídeo local y subtítulos HTTPS. Cada petición local debe coincidir exactamente con la ruta activa; no lleva Authorization, Cookie, Referer u Origin del proveedor.
- `MediaUrlPolicy` sigue rechazando HTTP local para los enlaces directos. La excepción Android se limita al host exacto `127.0.0.1`; no se abre HTTP para toda la red.
- No se registran magnets, tokens locales, trackers privados ni excepciones nativas crudas. Historial y guardado de posición siguen usando el título/episodio, no la URL local.

La capa Kotlin original de Nuvio se recompila en `:torrent-engine` con Kotlin 2.1.21. El AAR original usaba metadata Kotlin 2.3, incompatible con el compilador de Aetherio. No se desactiva la comprobación de metadata ni se actualiza toda la aplicación. Las bibliotecas nativas se extraen sin modificar del AAR verificado; la receta está en [la procedencia](../third_party/nuvio-engine/README.md).

## Compilar y comprobar

Requisitos: JDK 17, SDK Android 36, Gradle wrapper incluido. Configura `JAVA_HOME` y `ANDROID_HOME`. Para catálogo TMDB autenticado, configura tu propio token de lectura en la variable `TMDB_READ_ACCESS_TOKEN` o `tmdb.readAccessToken` de `local.properties` (archivo ignorado por Git).

```powershell
.\scripts\android.ps1 -Action verify
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
.\scripts\android.ps1 -Action verifyRelease
```

El módulo torrent usa las bibliotecas nativas incluidas: para compilar la aplicación no necesitas NDK, CMake ni descargar el motor en runtime. El script verifica SHA-256 del AAR antes de extraerlas. Reconstruir el núcleo nativo sí exige el toolchain upstream descrito junto al archivo fuente.

Las pruebas JVM cubren hash, codec, validación, selección, capacidades, espacio y ciclo de sesión con tiempo virtual. Las pruebas Android incluyen cancelación en segundo plano/recreación y un torrent privado de un vídeo generado para pruebas, servido por un webseed local controlado. Esa prueba verifica bloques mediante el motor real y solicita el primer frame a Media3; no usa títulos comerciales ni confirma disponibilidad de torrents de Provider Hub. El recorrido magnet/peers públicos requiere una comprobación independiente en la red del usuario.

### Smoke JNI sobre el APK release minificado

El runner habitual de pruebas debug depende de nombres Kotlin no minificados y no debe ejecutarse sin más contra una release R8. Se incluye un harness de instrumentación Java basado solo en el framework Android para comprobar carga, creación, constructores JNI y resultados nativos del APK release instalado:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest -PtorrentReleaseSmoke=true
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w com.panoli707.aetherio.nativeapp.test/com.panoli707.aetherio.nativeapp.player.torrent.TorrentReleaseInstrumentation
```

El resultado esperado empieza por `OK: minified JNI`. Esta comprobación no accede a una swarm pública. Para volver a la suite normal, reconstruye el APK de pruebas **sin** `-PtorrentReleaseSmoke=true` y vuelve a instalar el APK debug y el APK de pruebas. El harness solo existe en el APK de pruebas, no se exporta en la aplicación publicada.

Los resultados efectivamente ejecutados se registran en las notas de la versión; no debe confundirse un build correcto con una prueba física de cada ABI. El AAR incluye armv7, arm64, x86 y x86_64. Si no se dispone de dispositivo ARM o entorno de páginas 16 KiB, esas ejecuciones quedan explícitamente pendientes.

## Publicación y licencia

Aetherio se publica bajo GPL-3.0-or-later con el código de la integración y del wrapper. Se conserva el archivo fuente fijado del núcleo, sus scripts y avisos de dependencias. Consulta [LICENSE](../LICENSE) y [third_party/nuvio-engine](../third_party/nuvio-engine/README.md).

El motor upstream está en desarrollo y no se declara production-ready. La versión se ofrece como preliminar. El token TMDB incorporado en un APK puede recuperarse aunque esté ofuscado; el material generado y las credenciales locales no se publican en Git. Publicar un APK con token requiere autorización explícita mediante el flujo de mantenimiento existente.
