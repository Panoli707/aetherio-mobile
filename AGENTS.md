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
- El AVD principal compartido es `Aetherio_QA_API34`: usarlo para Artwork, Player, Settings y pruebas generales. `scripts/qa-emulator.ps1 -Action Start` reutiliza la instancia existente; no crear un AVD por cada tarea o corrección.
- `Pixel_7` se conserva solo para compatibilidad con Android 17. Un AVD adicional requiere una necesidad de aislamiento, API o pantalla concreta y aprobación; comprobar siempre el nombre del AVD y el serial antes de instalar o instrumentar.
- Antes de reutilizar un APK de pruebas, comprobar su runner con `adb -s <serial> shell pm list instrumentation`. Un APK de smoke puede registrar un runner especial: reconstruir solo `:app:assembleDebugAndroidTest` con la configuración general, no crear otro dispositivo por ese motivo.
