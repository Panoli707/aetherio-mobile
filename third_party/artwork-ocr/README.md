# OCR de portadas

- Motor: `cz.adaptech.tesseract4android:tesseract4android:4.9.0`, variante Standard, desde JitPack restringido a ese grupo.
- Fuente y licencias: https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0
- Modelos: `eng.traineddata` y `jpn.traineddata`, sin modificar, de https://github.com/tesseract-ocr/tessdata_fast/tree/4.1.0
- Se incluyen en `app/src/main/assets/artwork-ocr/`; sus licencias y las de las bibliotecas nativas están en `app/src/main/assets/licenses/` y se pueden consultar en Ajustes.

SHA-256 de los modelos:

```text
7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2  eng.traineddata
1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d  jpn.traineddata
```

El reconocimiento funciona sin servicios de Google, sin descarga de modelos y sin telemetría. Se ejecuta en segundo plano sobre una copia de hasta 960 píxeles de lado mayor; no reduce la resolución utilizada para dibujar la portada. Las clasificaciones se reutilizan en una caché de memoria limitada a 128 imágenes. El motor se libera después de cada análisis.

No se presupone que una imagen sin idioma en TMDB carezca de texto. El texto inferior o central detectado activa la búsqueda de hasta cuatro alternativas del mismo ámbito (obra o temporada). Si no hay una alternativa válida, el logo o título se coloca debajo de la ilustración. El OCR no garantiza reconocer todos los estilos tipográficos.
