# Una identidad visual por título desde 0.20.0

> **Corrección 0.20.2:** la identidad conserva una selección por formato: póster vertical y fondo horizontal real. Las tarjetas apaisadas ya no encajan un póster vertical con franjas. El registro revisado contiene 13 pares verticales y ocho horizontales; incluye Naruto, Frieren y Hyouken, además de películas y series. Se comprobaron en vivo 21 portadas de 1280 × 1920 o 1280 × 720 y 13 logos transparentes. `scripts/prepare-artwork-review.py` permite comparar candidatos de los endpoints raíz sin modificar el registro. La equivalencia AniList 20 → TMDB 46260 es solo de arte (Naruto, título original ナルト, estreno 2002); no cambia la reproducción.
>
> Los logos se recuperan independientemente de la inspección de la portada; se validan también los nombres originales y los recursos incluidos pueden utilizarse sin red. Si la portada contiene texto o su estado es incierto, el logo o título se muestra debajo, nunca se oculta ni se superpone sobre texto incrustado. Tras mostrar la primera imagen, Inicio y ficha pueden adoptar una única alternativa limpia validada por OCR local; se descarga antes de sustituirla y se guarda por formato. Las tarjetas no hacen OCR. Un resultado incierto conserva la portada, y la ausencia de fondo horizontal no fija un póster vertical como sustituto.
>
> Las descripciones inferiores de una sola ilustración para ambas orientaciones, ausencia de cambios de portada y bloqueo de logos por OCR de portada son históricas y no corresponden a 0.20.2. Se conservan preferencias, biblioteca, historial y registros verticales válidos anteriores.

> **Corrección 0.20.1:** la validación OCR descrita abajo ya no bloquea la portada ni las tarjetas. Se fija y muestra una imagen del título principal sin esperar metadatos adicionales si el catálogo ya la proporciona. Si no hay una selección limpia, se admite el póster del título y se omite el logo superpuesto mientras haya texto o incertidumbre. Solo la ficha analiza la composición, después de mostrar la imagen; las tarjetas no hacen OCR. La validación opcional de logos va separada. Los registros válidos antiguos siguen siendo compatibles. Véanse los cambios actuales en [0.20.1](../releases/0.20.1.md). El resto de este documento describe el diseño original y los límites de 0.20.0.

## Comportamiento

Inicio, fichas y tarjetas de catálogo comparten una selección de portada por película o serie. Cambiar la temporada modifica los episodios, no la portada ni el logo. Las imágenes de episodios siguen siendo fotogramas del episodio, no portadas nuevas de la serie.

Se conservan la posición del logo, el margen inferior de 8 dp, el degradado y los marcos existentes. La misma ilustración se utiliza en horizontal; no se cambia a otro banner al girar. Las tarjetas horizontales ajustan la portada dentro de su marco.

La selección se prepara fuera de pantalla. Mientras se resuelve o descarga, hay un marco neutro; no se muestra un póster provisional y después otro. Una portada rechazada nunca se dibuja. Si falla la descarga de una portada ya elegida, no se sustituye silenciosamente por otra.

## Selección y límites

`canonical-artwork.json` contiene siete parejas revisadas visualmente por ID de TMDB: Los diarios de la boticaria, Jujutsu Kaisen, Dragon Ball, Breaking Bad, El club de la lucha, Interstellar y El viaje de Chihiro. Se compararon seis portadas y hasta cuatro logos por título. Las imágenes elegidas no incorporan el título y los logos no incluyen temporada ni arco. Los números de la tabla periódica de Breaking Bad son parte del diseño, no temporadas. **No se ha revisado manualmente todo el catálogo externo.**

Para otros títulos, `CanonicalArtworkResolver` consulta imágenes del título completo, priorizando idioma, resolución y valoración según el recurso. Una etiqueta de idioma neutro no prueba la ausencia de texto. Un OCR local examina los candidatos antes de mostrarlos: se rechazan letras o números detectados en portadas. Los logos deben tener transparencia y texto compatible con el nombre, sin números ajenos al título, marcas de temporada, partes ni palabras adicionales. Un OCR incompleto no equivale a una imagen limpia.

El OCR puede omitir letras estilizadas o rechazar nombres válidos en otro idioma. Tampoco valora subjetivamente el mejor diseño. Las selecciones manuales permiten corregir esos casos. Si no se valida una portada limpia se conserva el marco neutro; si no se valida un logo se usa el nombre escrito, sin inventar un logo oficial ni reutilizar otro de temporada.

Se inspeccionan hasta diez imágenes y ocho variantes de logo. Hay dos selecciones simultáneas y el OCR se serializa fuera de la interfaz. Después de los metadatos, hay presupuestos cooperativos de doce segundos para portada y seis para logo; la inicialización nativa no ofrece interrupción dura. **La primera búsqueda automática puede tardar más que una descarga simple.** Las imágenes TMDB se fijan en una variante de hasta 1.280 px de ancho, evitando originales de tamaño arbitrario; no se reescriben URLs firmadas ni de otros proveedores.

## Persistencia e identidad

La pareja validada se guarda en `canonical_artwork_v1`, separada de favoritos, historial y preferencias. No caduca ni se vuelve a sortear por temporada, actualización de metadatos o reinicio del proceso. Si no se valida un logo al fijar la portada, el nombre escrito queda como respaldo estable; una revisión explícita del registro incluido puede corregirlo en otra versión. Una ausencia de portada debida a red u OCR incompleto no se guarda permanentemente.

Las equivalencias anime utilizan solo las tablas de IDs verificadas. Las partes AniList de una serie TMDB comparten elección. Las películas conservan su identidad: no se agrupan por franquicia ni por semejanza de nombres. Los títulos sin equivalencia conservan su identidad de proveedor.

Las cachés antiguas pueden contener arte de temporada, pero no deciden la nueva selección: se consultan los recursos del título completo sin heredar esas imágenes. No se borran datos personales durante la actualización.

## Corregir una selección

1. Confirmar el ID y las equivalencias, sin deducirlas del nombre.
2. Comparar arte del título completo a tamaño real y con la composición actual, no imágenes de temporada.
3. Elegir una portada nítida sin título/logotipo y un logo transparente con solo el nombre. No recortar números para simular un logo oficial diferente.
4. Añadir o corregir una única entrada en `app/src/main/resources/canonical-artwork.json`, con URL, proporción y evidencia de revisión. El registro revisado prevalece sobre elecciones automáticas guardadas.
5. Ejecutar pruebas de identidad, persistencia y geometría; revisar Inicio y ficha. No modificar imágenes de episodios ni borrar datos.

El catálogo antiguo de logos anime se conserva como candidatos con procedencia, no como selección obligatoria por temporada. Cada candidato automático debe superar la validación antes de mostrarse.
