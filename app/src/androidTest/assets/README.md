# Fixture torrent de pruebas

`torrent-fixture.mp4` es un vídeo sintético azul de tres segundos con una onda sinusoidal de 440 Hz. Se generó para las pruebas de Aetherio; no contiene obras audiovisuales de terceros. Se ofrece bajo CC0-1.0 (dominio público): https://creativecommons.org/publicdomain/zero/1.0/.

SHA-256: `911f08a8cee2153b0790d7b0f0550045a8d70f806f4b3c725a0d852e8d33f26d`.

Receta usada (los bytes pueden variar con la versión de FFmpeg):

```sh
ffmpeg -f lavfi -i 'color=c=blue:s=160x90:r=15:d=3' -f lavfi -i 'sine=frequency=440:duration=3' -c:v libx264 -pix_fmt yuv420p -profile:v baseline -c:a aac -movflags +faststart -shortest torrent-fixture.mp4
```

La instrumentación construye metadatos v1 con SHA-1 de sus piezas y un webseed local. No consulta un tracker público ni descarga contenido comercial.
