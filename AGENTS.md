# Publicación

- Cuando el usuario solicite publicar todos los commits, la tarea incluye publicar una nueva release de GitHub con su APK; no terminar solo con el push.
- Incrementar `versionCode` y la versión, verificar la compilación final, las pruebas pertinentes y la firma compatible antes de publicar.
- Usar una etiqueta nueva y mantener el canal preliminar mientras el proyecto siga en preview. No sobrescribir releases ni mover etiquetas publicadas.
- Adjuntar `Aetherio.apk`, las fuentes de terceros necesarias y `SHA256SUMS.txt`; incluir los metadatos de actualización que genera `scripts/prepare-release.ps1`.
- Conservar los datos y la compatibilidad de actualización. No desinstalar ni borrar datos del teléfono personal.
- Esta regla no autoriza desplegar Workers ni otros servicios.
