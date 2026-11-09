# Rendimiento del desarrollo y mantenimiento del disco

Diagnóstico local del 4 de noviembre de 2026 sobre la revisión base `63985e2` (0.14.6). No modifica funciones de la aplicación ni publica una release. Los tiempos dependen del equipo y de su carga.

## Resultados confirmados

### Arranque de Gradle

Comparación de `:app:assembleDebug` sin cambios, JDK 17, heap de 1 GiB, un worker, sin build cache ni configuration cache. Se realizó un calentamiento y tres mediciones por configuración; no se ejecutó `clean`.

| Configuración | Mediciones | Mediana |
| --- | --- | --- |
| Sin daemon reutilizable, carga habitual | 45,92 / 49,02 / 55,59 s | 49,02 s |
| Daemon reutilizable caliente, misma carga habitual | 10,14 / 9,67 / 6,98 s | 9,67 s |

La mediana se redujo un **80,3 %**. Todas las ejecuciones conservaron las 66 líneas de tareas `UP-TO-DATE`; el ahorro no procede de omitir tareas o pruebas. Este porcentaje describe exclusivamente la compilación sin cambios, no una recompilación completa ni una publicación.

El primer arranque con daemon siguió costando 46,10 s: reutilizar el proceso importa más que añadir la opción a una única ejecución aislada.

### Carga del equipo

Durante la comparación inicial se observaron 95–96 % de memoria comprometida, CPU media por ejecución de aproximadamente 81–93 % y actividad de entrada de páginas. Estas métricas son del sistema completo; no atribuyen todo el consumo a Gradle ni prueban por sí solas una causa única.

Tras cerrar el juego el usuario, la RAM disponible pasó de aproximadamente 1,2 a 6,0 GiB y la memoria comprometida observada bajó al 67 %. Android Studio y el emulador permanecieron abiertos.

Con menor carga, daemon caliente y dos workers, las tres compilaciones sin cambios tardaron **2,93 / 2,95 / 2,84 s**, mediana **2,93 s**. Cambiaron tanto la carga como el número de workers: no se puede atribuir toda la diferencia a uno de esos factores ni asegurar que dos workers aceleren una recompilación.

### Recompilación y validación final

La prueba de edición utilizó un archivo Kotlin temporal sin referencias desde la aplicación, retirado antes de la validación final. Su objetivo es distinguir arranque de Gradle y trabajo real de compilación, no simular todas las pantallas o tipos de cambio.

| Prueba exploratoria, menor carga y heap de 1 GiB | Tiempo |
| --- | ---: |
| Añadir el archivo, sin daemon reutilizable, un worker | 138,13 s |
| Modificar su constante, daemon caliente, un worker | 53,15 s |
| Volver a modificar su constante, daemon caliente, dos workers | 66,98 s |

Cada fila es una única medición y la primera añade un archivo mientras las siguientes lo modifican: no constituyen una comparación aislada ni permiten prometer ese ahorro para cualquier cambio. Sí muestran que la recompilación real sigue costando más que un build sin cambios y que aumentar workers no ayudó en esta muestra. Por ello no se aumentó el paralelismo predeterminado.

El perfil de la primera edición separa aproximadamente **77,59 s en Kotlin**, **17,77 s en DEX**, **13,92 s de arranque** y **8,82 s de configuración**. La generación del material TMDB permaneció `UP-TO-DATE`: no fue la causa de la recompilación. La resolución de dependencias sumó menos de un segundo en ese perfil.

### Validación final

- Una única invocación comprobó `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleRelease`, `testReleaseUnitTest` y `lintRelease` en **104,78 s** de tiempo externo medido. No es una compilación fría ni debe compararse como tal con publicaciones históricas.
- Los informes JVM de cada variante contienen **239 casos: 238 aprobados y uno opcional omitido**, sin fallos ni errores. Gradle consideró ambas tareas de test `UP-TO-DATE` y reutilizó esos resultados válidos; no se presenta como una nueva ejecución de los 478 casos.
- Lint debug y release terminaron con **cero errores y 61 advertencias por variante**.
- Se ejecutaron los seis tests aislados del script: opciones de desarrollo/release, límites de recursos, aislamiento, vista previa, prioridad del entorno de proceso y propagación de fallos.
- El script actualizado también se probó contra Gradle real: `assembleDebug -LowMemory -PublicDistribution -Profile` terminó correctamente, con las 59 tareas accionables reutilizadas. El primer arranque de esa comprobación costó 22 s según Gradle.
- Firma del APK release compatible, alineación `zipalign -P 16` y escaneo de 657 entradas para verificar que la configuración local no aparezca en texto plano.
- El archivo de sonda ya no existe en fuentes ni en las clases debug. No hay cambios en el código funcional de `app` ni de `torrent-engine`.
- No se reinstaló la app, no se ejecutaron pruebas UI adicionales ni se modificaron datos de dispositivos. Se conservaron Android Studio y el emulador existente.
- No se midieron tiempos nuevos de instalación, red o publicación: no se publicó una release en esta intervención. Las eliminaciones de AVD persistentes o componentes SDK también quedaron fuera por requerir aprobación específica.

## Cambios aplicados al flujo de trabajo

- Gradle permite reutilizar el daemon, con cierre automático tras cinco minutos de inactividad.
- Se conserva el heap predeterminado de 2 GiB y el límite de cuatro workers del proyecto. No se cambian por un supuesto beneficio no demostrado.
- `scripts/android.ps1` reutiliza el daemon en desarrollo y mantiene `--no-daemon` para las tareas release.
- `-LowMemory` permite usar un worker y heap de 1 GiB sin repetir argumentos manualmente. Conviene cerrar cargas pesadas antes de validar releases.
- `-NoDaemon` permite una ejecución aislada también en desarrollo. `-MaxWorkers` y `-HeapMiB` sirven para experimentos explícitos, no son una recomendación de aumentar recursos indiscriminadamente.
- `-Profile` genera un informe HTML local; `-WhatIf` muestra el comando sin ejecutar Gradle.
- La configuración de proceso de `JAVA_HOME` y `ANDROID_HOME` tiene prioridad sobre la configuración de usuario, para que una selección explícita no se sobrescriba silenciosamente.
- Configuration cache permanece deshabilitada porque la configuración de compilación depende de ajustes locales. La tarea de material TMDB continúa sin build cache.

El daemon puede permanecer en memoria hasta su cierre por inactividad. `-NoDaemon` no cierra otros daemons ya existentes; no se deben terminar procesos de Android Studio u otros trabajos para recuperar memoria sin autorización.

## Uso diario

Ejecutar desde la raíz del proyecto con JDK y SDK configurados.

### Comprobar rápidamente un cambio visual

```powershell
.\scripts\android.ps1 -Action assembleDebug -LowMemory
```

Esto compila y empaqueta, pero **no ejecuta pruebas JVM ni Android**. Añadir las pruebas afectadas según el cambio.

### Pruebas JVM y validación de integración

```powershell
.\scripts\android.ps1 -Action testDebugUnitTest -LowMemory
.\scripts\android.ps1 -Action verify -LowMemory
```

`verify` conserva compilación debug, pruebas JVM y lint. No sustituye las pruebas de interfaz, instalación o reproducción cuando el cambio las requiera. Para documentación, comprobar contenido y enlaces sin recompilar Android innecesariamente.

### Verificación de una publicación

```powershell
.\scripts\android.ps1 -Action verifyRelease -LowMemory -PublicDistribution
```

Conserva compilación release, pruebas JVM release y lint release. Añadir las pruebas Android pertinentes y verificar APK, firma compatible, versión, alineación y adjuntos antes de publicar. Una solicitud de publicar todos los commits incluye una nueva release según `AGENTS.md`.

No repetir verificaciones si la revisión, variante y configuración no cambiaron y el resultado sigue siendo válido. Un cambio posterior invalida las comprobaciones que afecte. No omitir errores ni reducir el conjunto final para mejorar artificialmente el tiempo.

### Obtener un perfil o revisar argumentos

```powershell
.\scripts\android.ps1 -Action assembleDebug -LowMemory -Profile
.\scripts\android.ps1 -Action verifyRelease -LowMemory -PublicDistribution -WhatIf
.\scripts\tests\android-script.tests.ps1
```

Los perfiles están en `build/reports/profile`. Los seis tests del script usan un Gradle ficticio aislado; comprueban argumentos y propagación de errores, sin iniciar builds Android.

## Limpieza realizada

Se inventariaron los archivos y su espacio asignado en NTFS, se obtuvo aprobación expresa y se comprobaron rutas, ausencia de enlaces, antigüedad, estabilidad del contenido y bloqueos antes de eliminar.

Las rutas siguientes son relativas a `%LOCALAPPDATA%\Temp\opencode`:

| Directorio eliminado | Espacio asignado aproximado |
| --- | ---: |
| `settings-navigation-validation\avd` | 5,225 GiB |
| `aetherio-torrent-0.13.0-verify\app\build` | 0,643 GiB |
| `aetherio-torrent-0.13.0-verify\torrent-engine\build` | 0,500 GiB |
| `aetherio-torrent-0.13.0-verify\build` | Menos de 0,001 GiB |
| `aetherio-artwork-qa\app\build` | 0,311 GiB |
| `aetherio-artwork-qa\torrent-engine\build` | 0,153 GiB |

Total asignado inventariado: **6,832 GiB**. El espacio libre de C: pasó de **130.276.442.112** a **137.627.693.056 bytes**, un aumento observado de **6,846 GiB**. La pequeña diferencia puede incluir actividad concurrente del sistema; no es una medida exclusiva de los archivos borrados.

Se conservaron las fuentes y configuraciones de las copias temporales, capturas y APK externos a las seis rutas, el SDK, la caché Gradle activa, todos los AVD habituales, los worktrees y el historial. Los hashes de las configuraciones locales protegidas se comprobaron internamente antes y después; no se publican sus contenidos.

Los registros de este diagnóstico y la lista de eliminación permanecen localmente en `%LOCALAPPDATA%\Temp\opencode\aetherio-performance-20261104-63985e2`. Pueden incluir rutas locales y artefactos de pruebas; no deben subirse indiscriminadamente ni eliminarse mientras se esté verificando el diagnóstico.

## Prevención y decisiones pendientes

1. No utilizar `clean`, borrar `.gradle` o invalidar cachés como mantenimiento rutinario: puede encarecer las siguientes compilaciones.
2. Evitar copias completas del proyecto y AVD nuevos para cada prueba visual. Registrar propietario y finalidad de cada entorno temporal.
3. Al finalizar un trabajo, cerrar solo sus procesos propios y retirar solo los temporales creados por ese trabajo que ya no se necesiten.
4. Revisar semanalmente temporales cerrados de más de 7–14 días. Antigüedad y extensión no bastan para autorizar un borrado; conservar evidencias de errores abiertos y datos no regenerables.
5. Cualquier limpieza futura comienza por inventario y vista previa, y requiere aprobación de rutas exactas. No hay tareas de borrado programadas.
6. Los AVD persistentes requieren una decisión separada sobre uso y conservación. La consolidación aprobada posteriormente se describe abajo; SDK/NDK, Descargas, Papelera y el worktree antiguo siguen protegidos.
7. Mover SDK o AVD a otro disco redistribuye espacio, pero no reduce el total ocupado. No se cambiaron sus ubicaciones.

Para revertir la optimización, restaurar únicamente los cambios de esta intervención en `gradle.properties` y `scripts/android.ps1`, conservando cualquier trabajo posterior. La limpieza es una eliminación definitiva de temporales aprobados: los builds se regeneran, pero el estado del AVD temporal eliminado no tiene restauración prometida.

## Un único emulador habitual

Para cambios de portadas, reproductor, Ajustes y pruebas generales se utiliza el mismo **`Aetherio_QA_API34`**, Android 14 / API 34. Actualizar el APK no requiere crear un dispositivo nuevo.

```powershell
# Ver qué instancia del AVD principal está disponible.
.\scripts\qa-emulator.ps1 -Action Status

# Iniciar el AVD existente o reutilizarlo si ya está ejecutándose.
.\scripts\qa-emulator.ps1 -Action Start

# Opcional: sin ventana o arranque en frío, siempre sin borrar los datos.
.\scripts\qa-emulator.ps1 -Action Start -Headless -ColdBoot
```

El script no crea, elimina ni reinicializa AVD; tampoco instala APK. Devuelve el serial que debe usarse explícitamente con ADB. Si el AVD está apagado y el puerto predeterminado está ocupado por otro dispositivo, se detiene con un error en lugar de tocar ese dispositivo. No cambia las opciones de una instancia que ya estaba funcionando.

Un APK de pruebas puede registrar un ejecutor especial, por ejemplo `ScrollPerformanceInstrumentation`. Comprobar `pm list instrumentation` en el serial elegido antes de llamar a `AndroidJUnitRunner`. Si no coincide, reconstruir solo `:app:assembleDebugAndroidTest` con la configuración general e instalarlo con `adb -s <serial> install -r`. No es un defecto del AVD ni una razón para crear otro.

Al arrancar solicita 2 GiB de RAM y dos núcleos. El arranque inicial puede tardar; confirmar `sys.boot_completed=1` antes de instalar o instrumentar. No recrear el dispositivo para resolver simplemente que todavía esté arrancando.

**`Pixel_7` se conserva como dispositivo opcional de Android 17**, no como otro entorno para cada corrección. Un dispositivo adicional solo se justifica por otra API, pantalla o aislamiento explícitamente necesario y aprobado.

### Consolidación completada

El usuario aprobó retirar `Aetherio_Artwork_QA_API34`, `Aetherio_Player_QA_API34` y la entrada huérfana `Aetherio_Settings_QA_API34`.

- El principal arrancó con Android 14 / API 34, recibió la actualización con `install -r` sin borrar datos y abrió Aetherio correctamente.
- **25 pruebas Android aprobadas** en ese único AVD: reproductor, Ajustes, selector de fuentes y distribución de portadas. Se reconstruyó únicamente el APK de pruebas para sustituir el runner especial de desplazamiento por el runner general.
- Se comprobó que `Status` y dos solicitudes de `Start` reutilizan el mismo dispositivo, incluso al solicitar otro puerto, sin crear AVD ni instancias adicionales.
- Artwork se cerró normalmente; Artwork y Player se retiraron con **AVD Manager** después de validar el principal. Se retiró solo el `.ini` huérfano de Settings, cuyo destino temporal ya no existía.
- Espacio asignado retirado: **5,871 GiB** de Artwork y **3,830 GiB** de Player. El espacio libre observado pasó de **145.628.901.376** a **156.045.406.208 bytes**, **9,701 GiB adicionales**; la actividad concurrente del sistema puede afectar a ese delta.
- El inventario final contiene únicamente **`Aetherio_QA_API34` y `Pixel_7`**, sin entradas dañadas. Sus archivos de registro y configuración se comprobaron antes y después y permanecieron intactos. No se eliminaron imágenes compartidas del SDK.
- La instancia del principal utilizada para verificar se cerró normalmente al terminar, para no dejar consumo de RAM innecesario. El AVD y sus datos siguen disponibles para el siguiente arranque.

Los registros, el resultado de pruebas y una copia pequeña de las configuraciones retiradas están en `%LOCALAPPDATA%\Temp\opencode\aetherio-avd-consolidation-20261104`. Esa copia no contiene las imágenes de datos eliminadas y no permite restaurar el estado de Artwork o Player.

Si Android Studio mantiene tarjetas antiguas en pantalla, actualizar Device Manager o volver a abrir esa pestaña; el inventario de AVD Manager ya no contiene esas entradas.
