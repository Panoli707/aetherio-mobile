# Auditoría de logos de anime

> La presentación actual sigue [la política canónica de 0.20.0](CANONICAL.md): un único par por título, sin cambios por temporada. Este inventario conserva la procedencia de candidatos históricos; no obliga a mostrarlos ni acredita que todos sean logos genéricos sin texto de temporada.

`anime-logo-audit.json` registra el inventario, fecha UTC, consultas cubiertas y estado por ID AniList. Es una instantánea reproducible de los catálogos actuales, **no un censo de todo el anime histórico**. Las fuentes no disponibles quedan en `unavailableScopes`; nunca se consideran auditadas.

## Resultado de esta publicación

284 títulos revisados: **144 logos nuevos** y cuatro ya incluidos. Quedan 136 casos pendientes: 69 identidades sin equivalencia inequívoca, 56 sin logo transparente válido en las imágenes consultadas, nueve fallos de consulta y dos recursos rechazados tras revisarlos visualmente. Cada caso conserva su nombre, ID y motivo en el informe JSON; un pendiente no significa que el logo no exista en ningún sitio de Internet.

Jikan no respondió durante la revisión: sus ocho consultas de Inicio quedan explícitamente sin auditar. Se utilizaron sus equivalentes disponibles en AniList como ampliación, sin presentarlos como resultados de Jikan. La temporada actual y los últimos 30 días de emisiones sí se paginaron por completo.

## Repetir la revisión

Desde la raíz del repositorio, con Python y Pillow disponibles:

```powershell
python scripts/audit-anime-logos.py
python scripts/review-anime-logos.py
```

El script lee el token TMDB del entorno o de `local.properties`, sin imprimirlo ni guardarlo en los resultados. Consulta las primeras páginas de Inicio, la temporada actualmente en emisión completa y todos los eventos de los últimos 30 días japoneses; cuando Jikan no responde registra el límite y añade consultas equivalentes de AniList, claramente identificadas. Respeta respuestas 429 y no añade consultas a la aplicación durante el uso normal.

Estados del informe:

- `already-bundled`: recurso incluido previamente.
- `added`: recurso transparente localizado, validado y añadido.
- `no-valid-transparent-logo`: identidad verificada, pero las imágenes consultadas no ofrecen un logo transparente con dimensiones válidas.
- `identity-not-verified`: no hay una equivalencia inequívoca; no se asocia un gráfico por parecido del nombre.
- `lookup-failed`: fallo de consulta, distinto de una ausencia de logo confirmada.
- `rejected-after-visual-review`: imagen descartada por texto promocional, mala calidad o identidad visual incorrecta.

Las equivalencias existentes por ID y temporada tienen prioridad. Una nueva equivalencia exige título original exacto, año de estreno, género animación e idioma original compatible; la ambigüedad deja el caso pendiente. En temporadas posteriores se consulta su endpoint específico, sin reutilizar automáticamente el logo de la primera. La tabla no modifica identidades de reproducción ni portadas.

## Recursos y presentación

`app/src/main/resources/anime-logo-catalog.json` conserva IDs, tipo, temporada, URL original y SHA-256 de cada PNG añadido. Los archivos están en `app/src/main/assets/anime-logos/`; solo se eliminan márgenes transparentes, se limita la resolución y se añaden cuatro píxeles de margen. Se conservan colores y proporciones. La aplicación prioriza los cuatro overrides anteriores, después este catálogo, después los recursos remotos y finalmente el título escrito.

Los gráficos y marcas son de sus respectivos titulares, no de Aetherio. TMDB proporciona metadatos e imágenes de terceros: esta documentación no concede derechos sobre ellos ni implica afiliación. Véase también `third_party/anime-logos/README.md` y los avisos incluidos en el APK.

La revisión visual se conserva en `logo-review.json`: corrige los logos de Bleach y Tokyo Ghoul que el proveedor mezclaba con secuelas, rechaza dos imágenes no aptas y añade el logo de Reincarnated as a Sword II desde su sitio oficial. Las decisiones son reproducibles mediante el segundo script, que comprueba hashes antes de sustituir o retirar archivos generados por la propia auditoría.

Inicio y ficha comparten `IntegratedDetailHero`: la imagen no crece para alojar el logo. Desde 0.16.5, el marco completo del logo termina 8 dp antes del borde inferior de la portada, independientemente del inicio del degradado. El degradado de la fotografía conserva su inicio al 86 % de la altura; el logo se dibuja después, por encima de él, para conservar sus colores. La imagen del logo y su contorno se alinean al borde inferior del marco, evitando el hueco que dejaba elevado un logo ancho dentro de un marco alto. El logo mantiene su proporción y se reduce en imágenes muy bajas. Si no existe un logo válido, el título escrito permanece en la misma zona, con elipsis para textos largos y sin crear una fila inferior. El OCR sigue buscando alternativas de portada, pero no puede volver a mover el logo fuera de la imagen.

Desde 0.19.0, la primera imagen válida no espera al OCR: los originales y las alternativas se preparan después, conservando una sola portada visible. Sin una alternativa real no se ejecuta OCR. La ficha puede mostrar su imagen general mientras se resuelve la temporada inicial. Véanse [diagnóstico, mediciones y límites](../CARGA_Y_TRAILERS.md). No se modifican los logos incluidos ni sus asociaciones.

Antes de publicar: comprobar hashes, transparencia y asociaciones con las pruebas JVM e instrumentadas; revisar capturas reales de anime, serie y película en Inicio y ficha. Los casos pendientes del informe conservan el título escrito y pueden completarse en futuras revisiones con evidencia verificable.
