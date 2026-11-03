# Provider Hub en Mobile

## Alcance

Esta integración de desarrollo usa `https://aetherio-provider-hub.xoolinnbeats.workers.dev` como único resolvedor **predeterminado** de streams. No despliega Workers ni publica una versión. Los addons personalizados y las fuentes opcionales activadas expresamente siguen disponibles.

El contrato actual del Hub solo ofrece `stream` para películas y series con IMDb. **Inicio, búsqueda, fichas y episodios conservan TMDB, AniList y Cinemeta integrados.** Pasar también todos los catálogos y metadatos por el Hub requiere trabajo de backend pendiente: no existen recursos `catalog` ni `meta` en este contrato. `/addons.json` es un directorio, no un proxy de streams.

Mobile no descarga `/scripts/`, no ejecuta `scrapers`, no consulta proveedores individuales indicados por el manifiesto y no replica la selección de hasta seis proveedores ni la reserva de Torrentio que realiza el servidor. Tampoco utiliza `/admin/*` ni necesita `ADMIN_TOKEN`. El Hub resuelve fuentes; los bytes de vídeo se solicitan a la URL de origen, no necesariamente al Worker.

## Inicialización y migración

- `ProviderHub.kt` incorpora el contrato público `com.aetherio.hub`, con `resources: ["stream"]`, tipos `movie/series` y prefijo `tt`.
- `ProviderDefaultsStore` registra el Hub localmente una vez, incluso sin red, en `aetherio_provider_defaults/hub_registered_v1`. La inicialización repetida no duplica ni reinstala el proveedor.
- El registro administrado es independiente de `AddonStore`, que cifra los addons añadidos por el usuario. La migración no reescribe ese almacén ni toca cuentas, biblioteca o historial. Si no puede leerlo, no lo sobrescribe con una lista vacía.
- Los cinco adaptadores antiguos estaban incorporados en código y se activaban sin procedencia individual. Se conserva intacta la preferencia antigua `aetherio_source_settings/desktop`, pero no se consulta automáticamente. Las nuevas activaciones explícitas usan `hub_optional_sources_v1`, vacío por defecto. No se borran addons que coincidan con una fuente antigua: el almacén existente contiene configuraciones añadidas expresamente por el usuario.
- AnimeAV1 continúa siendo un adaptador nativo independiente y opcional; no se sustituye por AnimES. Activar fuentes adicionales puede duplicar parte del trabajo del Hub y no es necesario para la instalación predeterminada.

Una caída del Hub deja un error con reintento. No borra su registro, no reinstala addons ni activa otros proveedores. Los addons personalizados existentes se mantienen como fuentes explícitas, no como sustitutos silenciosos activados al fallar el Hub.

## Resolución y reproducción

Las rutas son `/stream/movie/ttNNNNN.json` y `/stream/series/ttNNNNN:temporada:episodio.json`. Se conserva el IMDb completo. Las fichas unificadas usan las equivalencias y desplazamientos existentes antes de consultar el Hub. Sin correspondencia IMDb válida se informa del problema; nunca se convierten sus dígitos en un ID TMDB ni se inventa un ID.

Se reutilizan `SourceProtocol`, `SourceRepository`, el selector de fuentes y Media3. El resultado conserva `url`, `infoHash`, `fileIdx`, `sources`, `subtitles`, cabeceras y `behaviorHints`; `rawStream` conserva además el objeto original validado en memoria. No se persisten respuestas de streams ni se almacenan indefinidamente listas vacías.

- HTTP: solo URLs que superan la validación: HTTPS y la excepción HTTP preexistente para `shegu.net` y subdominios. Una cadena cifrada, sin esquema o malformada se rechaza, sin anteponer `https://`. Los indicadores `verified`, `isReal` o `notWebReady` no garantizan reproducción.
- Torrents: se retienen hashes y magnets válidos, incluso sin `url`, con índice de archivo y trackers. **No hay motor torrent en Mobile.** Se muestran con reproducción deshabilitada. `SourcePlayback` impide pasarlos al datasource HTTP de Media3; no se añaden dependencias torrent en esta tarea.
- Cabeceras: se combinan las cabeceras del stream y `behaviorHints.proxyHeaders.request`, rechazando conflictos, controles e inyección. Para el Hub se admiten también `Accept`, `Accept-Language`, `Authorization`, `Cookie` y `X-Requested-With`; las cabeceras de transporte como `Host` no se aceptan. Son valores devueltos por el proveedor, nunca el token local TMDB. Las credenciales de reproducción se eliminan al cambiar de origen o usar HTTP. Los campos de respuesta del proxy se conservan como metadatos; Mobile no es un proxy HTTP.
- Subtítulos: se conservan URL, ID, idioma y MIME. Media3 recibe los formatos identificados VTT, SRT, SSA/ASS y TTML; no se promete compatibilidad con todos los formatos o codecs.
- El Hub usa un timeout total de 45 segundos por intento y como máximo dos intentos; solo se repiten fallos transitorios de transporte o HTTP 408/502/503/504. JSON inválido, errores de contrato y listas vacías no disparan reintentos automáticos. La cancelación cancela OkHttp, incluso durante la lectura del cuerpo. Los cuerpos y colecciones tienen límites de tamaño.

## Comprobación local

Requisitos: JDK 17, SDK Android configurado y un emulador o dispositivo de pruebas. No desinstales ni borres datos del teléfono personal.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=1
```

Prueba pública opcional de los ejemplos reales, sin descargar vídeo ni iniciar torrents:

```powershell
$env:AETHERIO_HUB_LIVE_TESTS = 'true'
.\gradlew.bat :app:testDebugUnitTest --tests com.panoli707.aetherio.nativeapp.data.ProviderHubLiveTest --rerun-tasks --no-daemon --max-workers=1
Remove-Item Env:AETHERIO_HUB_LIVE_TESTS
```

El test consulta el manifiesto y los ejemplos `tt5311514` y `tt2560140:1:1`. Informa de resultados retenidos, torrents, HTTP válidos y entradas rechazadas; **no fija cantidades**, que dependen del servidor. No prueba reproducción en un dispositivo.

Para ejecutar las pruebas Android focalizadas, sustituye el serial por el de tu emulador de pruebas:

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class com.panoli707.aetherio.nativeapp.data.ProviderDefaultsStoreTest,com.panoli707.aetherio.nativeapp.data.SourceSettingsStoreTest,com.panoli707.aetherio.nativeapp.ui.ProviderHubUiTest,com.panoli707.aetherio.nativeapp.ui.AddonsSettingsTest com.panoli707.aetherio.nativeapp.test/androidx.test.runner.AndroidJUnitRunner
```

Verifica en Ajustes que el Hub figure integrado y las fuentes adicionales estén apagadas. Busca un título con IMDb, abre sus fuentes y comprueba por separado carga, error, lista vacía, resultados HTTP y torrents deshabilitados. Al repetir la inicialización debe seguir habiendo un único Hub y conservarse los proveedores propios. La prueba de migración usa preferencias y claves de prueba aisladas, no las del usuario.

## Resultados de la comprobación

- Compilados el APK debug y el APK de pruebas Android; `lintDebug` sin errores.
- Suite JVM final: **165 pruebas aprobadas, sin fallos ni omitidas**, incluido el test público opcional. Cubre rutas, campos HTTP/torrent, índices, cabeceras, aislamiento de credenciales por origen/transporte, JSON inválido, URLs malformadas, vacíos, timeout, reintentos y cancelación.
- Pruebas Android focalizadas en `emulator-5554` (Android 14): **12 aprobadas**. Incluyen registro inicial aislado sin red, inicialización repetida, migración cifrada, conservación de datos/preferencias, fuentes opcionales apagadas y selector HTTP/torrent.
- Regresiones Android: **16 aprobadas** en `VideoActivityTest`, `PlayerScreenTest` y `StartupScreenTest`. En total son **28 casos Android distintos**. Tras la última compilación se repitieron los 12 casos focalizados y los 2 de `VideoActivityTest`: **14/14 aprobados**. Estas regresiones usan estados y enlaces inválidos de prueba, no reproducción real de vídeos del Hub.
- `git diff --check` sin errores. No se modificaron versión, firma, manifiesto Android ni enlaces de descarga públicos. No se desplegaron Workers ni se publicó una release.
- `/health` y `/manifest.json`: HTTP 200; servicio `1.8.0`, identidad `com.aetherio.hub`, recursos `stream` para `movie/series`.
- El test Kotlin público también pasó, sin descargar vídeo ni ejecutar torrents. Resultado observado, no una cantidad garantizada:

| Ejemplo | HTTP del endpoint | Torrents conservados | URLs HTTP aceptadas | Entradas rechazadas |
| --- | --- | --- | --- | --- |
| `tt5311514` | 200 | 3 | 0 | 6 |
| `tt2560140:1:1` | 200 | 2 | 0 | 3 |

«URLs HTTP aceptadas» significa únicamente que la URL y los campos superarían las comprobaciones locales; no confirma acceso al archivo, decodificación ni reproducción. Los tests no fijan estos recuentos.

## Pendientes de backend y motor

En la comprobación inicial de ambos ejemplos se recibieron respuestas HTTP 200 con streams, pero sus entradas `url` no eran URLs HTTP directas válidas; los otros resultados eran torrents. Esto confirma respuesta del endpoint, **no disponibilidad de una fuente reproducible en Mobile**. Hace falta que el backend entregue URLs válidas o incorporar por separado un motor torrent para cubrir esos casos. No se han probado esos vídeos en un dispositivo, ni se promete reproducción universal.
