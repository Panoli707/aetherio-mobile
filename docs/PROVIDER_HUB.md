# Provider Hub en Mobile

## Alcance

Desde la versión preliminar **0.17.0**, Mobile combina el resolvedor `https://aetherio-provider-hub.xoolinnbeats.workers.dev/stream/...` con el directorio público `/addons.json`, igual que la sincronización de addons de PC. Los proveedores administrados compatibles se incorporan al abrir la app y al buscar o actualizar fuentes, sin recompilar el APK. Esta publicación no modifica ni despliega Workers. Los addons personalizados y las fuentes opcionales activadas expresamente siguen disponibles.

El resolvedor del Hub ofrece `stream` para películas y series con IMDb. `/addons.json` es un directorio, no un proxy: Mobile consulta el recurso `stream` de los addons habilitados y compatibles que anuncia. **Inicio, búsqueda, fichas y episodios conservan TMDB, AniList y Cinemeta integrados; Inicio añade Jikan/MyAnimeList para anime.** No se importan los catálogos de los addons. Las entradas que solo admiten deportes u otros identificadores se indican como incompatibles en Ajustes, sin inventar correspondencias.

Mobile no descarga `/scripts/`, no ejecuta `scrapers` ni utiliza `/admin/*` o `ADMIN_TOKEN`. El servidor sigue ejecutando los proveedores de su manifiesto; Mobile lee sus nombres e iconos para atribuir resultados y mostrar el inventario completo en Ajustes. Los bytes de vídeo se solicitan a la URL de origen, no necesariamente al Worker. Que un proveedor figure habilitado no garantiza resultados ni reproducción para todos los títulos.

## Sincronización del directorio

- `ProviderHubDirectory.kt` valida `/addons.json` y utiliza `manifestFallback` como contrato público de streams. Los nombres, logos, activación predeterminada, límites de consulta y filtros por prefijo de nombre proceden del directorio, no de una lista compilada.
- Desde **0.20.3**, la lectura pública admite colecciones auxiliares grandes dentro del límite de 512 KB y 32 niveles de anidación. Antes de validar cada contrato de streams se descartan catálogos, géneros y recursos ajenos a streams. Así, las listas extensas de un nuevo bridge no invalidan todo el directorio; los límites de manifiestos propios y respuestas de streams no se amplían.
- `HubDirectorySync` refresca al abrir o volver a la pantalla principal y cada cinco minutos mientras permanece activa. Los fallos completos o parciales reintentan a los 2, 10, 30 y después cada 60 segundos; el ciclo se detiene al salir y vuelve a consultar al regresar. No requiere actualizar el APK, un Worker ni ejecutar tareas con la app cerrada. Las peticiones públicas piden revalidación de caché.
- La caché se publica inmediatamente. Directorio e inventario del manifiesto se descargan y publican independientemente; una respuesta lenta no retiene la otra. La búsqueda de streams inicia el Hub integrado, los proveedores propios y los administrados guardados sin esperar el refresco; los proveedores recién descubiertos se incorporan a esa misma búsqueda.
- Las rutas relativas de addons se resuelven contra `https://trkll.aetherio.workers.dev`, como en PC; no contra el origen del Hub. Los destinos de manifiesto y los logos deben ser HTTPS.
- `hiddenInUi` no desactiva consultas: los agregadores siguen aportando resultados, agrupados por el proveedor indicado en su payload.
- Los últimos directorio y manifiesto públicos válidos se guardan en preferencias independientes. Un fallo de red o un documento parcial/malformado conserva la lista anterior. Una lista vacía válida sí retira los proveedores administrados. No se guardan respuestas de streams.
- Los addons propios tienen prioridad cuando coinciden por identidad de manifiesto; no se reescriben ni se borran. Las fuentes opcionales explícitas siguen separadas. Se deduplican destinos HTTP con las mismas cabeceras y torrents con el mismo hash/archivo, conservando las variantes de cabeceras o archivo.
- Los proveedores administrados usan por defecto 55 segundos y un intento. Se respetan los límites declarados por el directorio, acotados a 1–60 segundos y 1–2 intentos; los reintentos siguen limitados a fallos transitorios. La descarga del directorio y del manifiesto tiene un límite de 12 segundos por petición.

## Datos del selector

`SourceMetadata.kt` separa presentación y transporte. Las pestañas y tarjetas muestran nombre e icono del proveedor real, título/archivo, tamaño, indexer, duración, seeders e idiomas cuando esos datos existen. La duración puede proceder de la ficha o del episodio seleccionado. Las etiquetas de resolución, vídeo, audio e idiomas se obtienen de campos explícitos o de marcadores reconocibles del nombre/archivo; no se supone una calidad a partir de HLS ni se interpreta «AnimeAV1» como prueba del codec AV1.

Los 28 gráficos de etiquetas en `app/src/main/assets/stream-tags/` proceden de los recursos del proyecto PC aportado. Los gráficos y marcas pertenecen a sus respectivos titulares. Un fallo al cargar un logo o gráfico mantiene una alternativa escrita. Las etiquetas usan filas adaptables al ancho del móvil. Se conservan panel, fondo, navegación y controles del selector.

La identidad del addon de transporte no se sustituye por el nombre visible: cabeceras, selección de archivo torrent y subtítulos mantienen su ruta de reproducción. Los metadatos visuales incorrectos no descartan un destino válido; los subtítulos malformados de un proveedor administrado se omiten sin pasar URLs inválidas al reproductor. Los agregadores administrados admiten hasta 2.000 resultados bajo el límite de 2 MB y 32 niveles de anidación; las colecciones auxiliares siguen acotadas a 200.

## Inicialización y migración

- `ProviderHub.kt` incorpora el contrato público `com.aetherio.hub`, con `resources: ["stream"]`, tipos `movie/series` y prefijo `tt`.
- `ProviderDefaultsStore` registra el Hub localmente una vez, incluso sin red, en `aetherio_provider_defaults/hub_registered_v1`. La inicialización repetida no duplica ni reinstala el proveedor.
- El registro administrado es independiente de `AddonStore`, que cifra los addons añadidos por el usuario. La migración no reescribe ese almacén ni toca cuentas, biblioteca o historial. Si no puede leerlo, no lo sobrescribe con una lista vacía.
- Los cinco adaptadores antiguos estaban incorporados en código y se activaban sin procedencia individual. Se conserva intacta la preferencia antigua `aetherio_source_settings/desktop`, pero no se consulta automáticamente. Las nuevas activaciones explícitas usan `hub_optional_sources_v1`, vacío por defecto. No se borran addons que coincidan con una fuente antigua: el almacén existente contiene configuraciones añadidas expresamente por el usuario.
- AnimeAV1 continúa siendo un adaptador nativo independiente y opcional; no se sustituye por AnimES. Activar fuentes adicionales puede duplicar parte del trabajo del Hub y no es necesario para la instalación predeterminada.

Una caída del resolvedor no borra su registro ni activa fuentes opcionales. Los addons administrados del último directorio válido y los proveedores propios se consultan de forma independiente; sus resultados pueden seguir disponibles aunque falle el resolvedor.

## Resolución y reproducción

Las rutas son `/stream/movie/ttNNNNN.json` y `/stream/series/ttNNNNN:temporada:episodio.json`. Se conserva el IMDb completo. Las fichas unificadas usan las equivalencias y desplazamientos existentes antes de consultar el Hub. Sin correspondencia IMDb válida se informa del problema; nunca se convierten sus dígitos en un ID TMDB ni se inventa un ID.

Se reutilizan `SourceProtocol`, `SourceRepository`, el selector de fuentes y Media3. El resultado conserva `url`, `infoHash`, `fileIdx`, `sources`, `subtitles`, cabeceras y `behaviorHints`; `rawStream` conserva además el objeto original validado en memoria. No se persisten respuestas de streams ni se almacenan indefinidamente listas vacías.

- HTTP: solo URLs que superan la validación: HTTPS y la excepción HTTP preexistente para `shegu.net` y subdominios. Una cadena cifrada, sin esquema o malformada se rechaza, sin anteponer `https://`. Los indicadores `verified`, `isReal` o `notWebReady` no garantizan reproducción.
- Torrents: se retienen hashes v1 y magnets válidos, incluso sin `url`, con índice de archivo y trackers. Desde **0.13.0**, el motor integrado los convierte en una ruta HTTP local ligada a la sesión. `SourcePlayback` sigue impidiendo pasar magnets al datasource HTTP. La disponibilidad de participantes y la compatibilidad del dispositivo determinan si se puede reproducir. Consulta [el motor y sus límites](TORRENT_ENGINE.md).
- Cabeceras: se combinan las cabeceras del stream y `behaviorHints.proxyHeaders.request`, rechazando conflictos, controles e inyección. Para el Hub y los addons procedentes de su directorio se admiten también `Accept`, `Accept-Language`, `Authorization`, `Cookie` y `X-Requested-With`; las cabeceras de transporte como `Host` no se aceptan. La procedencia administrada no se restaura desde manifiestos ni desde el almacén de addons del usuario. Son valores devueltos por el proveedor. Las credenciales de reproducción se eliminan al cambiar de origen o usar HTTP. Los campos de respuesta del proxy se conservan como metadatos; Mobile no es un proxy HTTP.
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

El test consulta manifiesto, directorio, addons públicos compatibles (incluidos los agregadores ocultos) y los ejemplos `tt5311514` y `tt2560140:1:1`. Informa de resultados retenidos, torrents, HTTP válidos y entradas rechazadas; **no fija cantidades**, que dependen del servidor. No descarga vídeo ni prueba reproducción en un dispositivo.

Para ejecutar las pruebas Android focalizadas, sustituye el serial por el de tu emulador de pruebas:

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class com.panoli707.aetherio.nativeapp.data.ProviderDefaultsStoreTest,com.panoli707.aetherio.nativeapp.data.SourceSettingsStoreTest,com.panoli707.aetherio.nativeapp.ui.ProviderHubUiTest,com.panoli707.aetherio.nativeapp.ui.AddonsSettingsTest com.panoli707.aetherio.nativeapp.test/androidx.test.runner.AndroidJUnitRunner
```

Verifica en Ajustes que el Hub figure integrado y las fuentes adicionales estén apagadas. Busca un título con IMDb, abre sus fuentes y comprueba por separado carga, error, lista vacía, resultados HTTP y selección torrent. Al repetir la inicialización debe seguir habiendo un único Hub y conservarse los proveedores propios. La prueba de migración usa preferencias y claves de prueba aisladas, no las del usuario.

## Resultados históricos de la comprobación 0.12.0

- Compilados el APK debug y el APK de pruebas Android; `lintDebug` sin errores.
- Suite JVM final: **165 pruebas aprobadas, sin fallos ni omitidas**, incluido el test público opcional. Cubre rutas, campos HTTP/torrent, índices, cabeceras, aislamiento de credenciales por origen/transporte, JSON inválido, URLs malformadas, vacíos, timeout, reintentos y cancelación.
- Pruebas Android focalizadas en `emulator-5554` (Android 14): **12 aprobadas**. Incluyen registro inicial aislado sin red, inicialización repetida, migración cifrada, conservación de datos/preferencias, fuentes opcionales apagadas y selector HTTP/torrent.
- Regresiones Android: **16 aprobadas** en `VideoActivityTest`, `PlayerScreenTest` y `StartupScreenTest`. En total son **28 casos Android distintos**. Tras la última compilación se repitieron los 12 casos focalizados y los 2 de `VideoActivityTest`: **14/14 aprobados**. Estas regresiones usan estados y enlaces inválidos de prueba, no reproducción real de vídeos del Hub.
- La verificación original de la integración se hizo sin publicar ni modificar versión, firma o enlaces de descarga. Para la publicación autorizada de Mobile se incrementan `versionName` a `0.12.0` y `versionCode` a `18`, manteniendo paquete y firma. No se despliegan Workers.
- `/health` y `/manifest.json`: HTTP 200; servicio `1.8.0`, identidad `com.aetherio.hub`, recursos `stream` para `movie/series`.
- El test Kotlin público también pasó, sin descargar vídeo ni ejecutar torrents. Resultado observado, no una cantidad garantizada:

| Ejemplo | HTTP del endpoint | Torrents conservados | URLs HTTP aceptadas | Entradas rechazadas |
| --- | --- | --- | --- | --- |
| `tt5311514` | 200 | 3 | 0 | 6 |
| `tt2560140:1:1` | 200 | 2 | 0 | 3 |

«URLs HTTP aceptadas» significa únicamente que la URL y los campos superarían las comprobaciones locales; no confirma acceso al archivo, decodificación ni reproducción. Los tests no fijan estos recuentos.

## Disponibilidad y límites

En la comprobación inicial de ambos ejemplos se recibieron respuestas HTTP 200 con streams, pero sus entradas `url` no eran URLs HTTP directas válidas; los otros resultados eran torrents. El motor integrado en 0.13.0 permite intentar esos torrents, pero no repara URLs cifradas ni garantiza participantes o códecs. Los recuentos anteriores son históricos, no una prueba de reproducción de esos títulos. No se promete reproducción universal.
