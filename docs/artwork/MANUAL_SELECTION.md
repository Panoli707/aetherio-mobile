# Selección de portada y logo

La ficha ofrece un botón de tres puntos junto a **Reproducir**:

- **Elegir portada del medio**: alternativas verticales o fondos horizontales.
- **Elegir logo del medio**: alternativas de logo y **Usar texto**.
- **Restablecer predeterminado** elimina únicamente la elección manual de ese recurso y formato.

La cuadrícula adapta el flujo de `src/pages/Home/CardArtworkPicker.tsx` de
`trklll/aetherio` a Compose. Los logos se previsualizan completos, sin recortes.
Se consulta el recurso raíz por ID mediante los proveedores existentes; no se
buscan equivalencias por parecido del nombre ni se mezclan imágenes de temporadas.
La cantidad de alternativas depende de lo que ofrezca el proveedor.

Sin elección manual se usa la selección automática del proveedor, sin imponer
los pares de imágenes revisados del catálogo canónico. Se conserva la validación
de logos y la mejora opcional de portadas limpias del flujo existente.

El usuario puede elegir cualquier alternativa que ofrezca el proveedor, aunque
incluya texto, logos impresos, una temporada o un arco. No se ejecuta OCR ni se
restringe el logo por transparencia o por nombre. La imagen se muestra según el
formato de la opción seleccionada.

Las preferencias manuales viven en `artwork_selections_v1`, independientes de
los valores automáticos de `canonical_artwork_v1`, la biblioteca y el historial.
La clave combina el tipo de recurso, el formato y la identidad canónica verificada.
Las temporadas comparten la misma elección de su serie; cada película conserva
su identidad propia. Inicio, ficha y tarjetas observan las elecciones actualizadas.
Elegir una portada no modifica las miniaturas de episodios ni la reproducción.

El logo o texto de respaldo permanece dentro de la portada, a 8 dp del borde
inferior. Las elecciones vertical y horizontal se guardan por separado.
