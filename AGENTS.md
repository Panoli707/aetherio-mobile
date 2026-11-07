# Publicación

- Cuando el usuario solicite publicar todos los commits, la tarea incluye publicar una nueva release de GitHub con su APK; no terminar solo con el push.
- Incrementar `versionCode` y la versión, verificar la compilación final, las pruebas pertinentes y la firma compatible antes de publicar.
- Usar una etiqueta nueva y mantener el canal preliminar mientras el proyecto siga en preview. No sobrescribir releases ni mover etiquetas publicadas.
- Adjuntar `Aetherio.apk`, las fuentes de terceros necesarias y `SHA256SUMS.txt`; incluir los metadatos de actualización que genera `scripts/prepare-release.ps1`.
- Conservar los datos y la compatibilidad de actualización. No desinstalar ni borrar datos del teléfono personal.
- Esta regla no autoriza desplegar Workers ni otros servicios.

# Desarrollo y mantenimiento

- Validar solo lo pertinente durante la iteración: documentación y enlaces no requieren recompilar Android; los cambios de código requieren compilación y pruebas afectadas. No omitir la verificación final de una publicación.
- Usar `scripts/android.ps1` para las tareas habituales. En equipos con presión de memoria, añadir `-LowMemory`; desarrollo reutiliza el daemon y las tareas release conservan `--no-daemon`.
- No ejecutar `clean` ni invalidar cachés de forma rutinaria. No activar configuration cache ni cachear la tarea del material TMDB: la configuración lee una credencial local.
- No crear copias completas del proyecto o AVD nuevos para cada ajuste. Reutilizar entornos compatibles sin tocar datos personales; registrar y retirar solo los temporales y procesos propios cuando ya no se necesiten.
- Los borrados de temporales existentes requieren una lista exacta aprobada. No borrar SDK, AVD persistentes, cachés activas, claves, fuentes o historial para ahorrar espacio sin aprobación específica.
- El dispositivo Android predeterminado para desarrollo y QA es el Xiaomi 22101320G, serial ADB `6b591e1e`. Todas las pruebas instrumentadas, instalaciones y verificaciones visuales se ejecutan ahí con `adb -s 6b591e1e`; si no está conectado o autorizado, detenerse y pedir al usuario que lo conecte. No cambiar al emulador automáticamente.
- No usar emuladores para tareas normales. Solo usar `Aetherio_QA_API34`, `Pixel_7` u otro AVD si el usuario lo pide expresamente; los scripts `qa-emulator.ps1`, `qa-artwork-release.ps1` y `qa-regressions-release.ps1` son flujos heredados exclusivos del emulador.
- Para instrumentación Gradle, usar `scripts/android.ps1 -Action connectedDebugAndroidTest`; valida que el serial configurado sea el Xiaomi y fuerza `ANDROID_SERIAL` para evitar que Gradle elija el emulador. Actualizar APKs sobre la instalación existente con firma compatible; nunca desinstalar ni borrar datos del teléfono.
- Antes de reutilizar un APK de pruebas, comprobar su runner con `adb -s <serial> shell pm list instrumentation`. Un APK de smoke puede registrar un runner especial: reconstruir solo `:app:assembleDebugAndroidTest` con la configuración general, no crear otro dispositivo por ese motivo.
