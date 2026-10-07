# Quick Start — reconstrucción del flujo "Crear perfil"

Análisis de las 4 referencias (mockups de un sistema TV 16:9) y decisiones de
adaptación a la realidad de Aetherio.

## Escala

Las referencias son mockups 16:9 sin chrome: el diseño es **relative al ancho de
pantalla** (la misma retícula sirve de 1280 a 1920). Por eso todo se expresa en
`vw` con `clamp`, como ya se hizo con el pad de PIN: a 1920 da los valores de la
referencia y a 1280 escala proporcionalmente sin reorganizarse.

Medidas leídas de las referencias (sobre 1670 px de ancho de referencia):

| Elemento | x | ancho | alto | en vw |
|---|---|---|---|---|
| Bloque teclado | 270 | 380 | — | empieza en 16vw |
| Tabs de modo (pastilla) | 272 | 376 | 58 | 22.5vw × 3.5vw |
| Tab activa (blanca) | 283 | 112 | 50 | 6.7vw |
| Columna de teclas | 268 | 376 | — | pitch 64px = 3.83vw |
| Grilla de letras | — | — | 5 filas | pitch vertical 67px = 4.0vw |
| Glifo de tecla | — | — | ~40 | font 2.3vw |
| Tecla enfocada (cuadro) | 268 | 62 | 62 | 3.7vw |
| Fila ESPACIO/⌫/BORRAR | 286 | 344 | 30 | font 1.15vw |
| Título "Elige un nombre" | 985 | 360 | 50 | font 3.0vw, peso 600 |
| Campo con el nombre | 885 | 558 | 64 | 33.4vw × 3.8vw, radio 999 |
| Botón Continuar | 990 | 322 | 56 | 19.3vw × 3.35vw, radio 999 |
| Título "Elige una imagen" | 665 | 345 | 46 | font 2.75vw |
| Burbuja de avatar | 50 | 285 | 285 | 17vw, pitch x 340 = 20.4vw, pitch y 315 = 18.9vw |
| Título "Completar perfil" | 165 | 525 | 40 | font 2.15vw |
| Fila del resumen | 165 | 525 | 62 | 3.7vw, gap 22 = 1.3vw |
| Botón OK | 205 | 448 | 56 | 26.8vw |
| Tarjeta derecha | 895 | 615 | 680 | 36.8vw, radio 34 = 2vw |
| Avatar + badge | 1070 | 240 | 120 | círculos de 7.2vw solapados 2vw |
| Título bienvenida | 455 | 705 | 130 | font 3.2vw, peso 700 |
| Párrafo bienvenida | 445 | 780 | 160 | font 1.2vw, medida 47vw |
| Botón "Comenzar a ver" | 610 | 445 | 56 | 26.6vw |

## Color

Fondo `#1a1a1a` (idéntico al de la page de perfiles). Superficie de fila/campo
`#3a3a3c`. Tarjeta `#262628`. Texto `#fff` y `rgba(255,255,255,0.55)`. Acento:
blanco puro (píldora activa, tecla enfocada, botón primario).

## Adaptación a Aetherio (lo que cambia y por qué)

1. **Sin "Menor" / Apple Kids.** La referencia incluye un selector de edad
   (Apple Kids). Aetherio no tiene perfiles infantiles: la fila se sustituye por
   **PIN**, que es la protección real del perfil (4 dígitos).
2. **El párrafo de privacidad de Apple se reescribe.** Aetherio no manda datos a
   un servidor de cuentas: el perfil vive en este equipo. El texto real es el
   que dice `localProfiles.ts` (hash SHA-256 con salt, nunca el PIN en claro).
3. **El enlace "Consulta cómo se utiliza tu información" no puede ser un link
   muerto.** No hay web pública de Aetherio en el repo, así que se convierte en
   un control real: **"Qué se guarda en este equipo"**, que despliega la lista
   real de lo que guarda un perfil.
4. **Las 4+ burbujas de avatars son skeletons.** El usuario lo pidió: las
   imágenes por defecto aún no existen. 4 esqueletos + una quinta burbuja real
   "añadir imagen propia" que abre el file picker.
5. **Tipografía: Inter**, no SF. El usuario revirtió la app a Inter y esta page
   es parte de la app.

## Fondo compartido

Las referencias no traen fondo: usan el de la page de perfiles. Se extrae el
bloque `.profile-selection-bg` (img avatares con `blur(70px) saturate(1.3)` +
tint + radial + vertical + bottom) a un componente `ProfileBackdrop` que usan
tanto Profiles como QuickStart, con la imagen opcional: sin perfil aún, solo
los degradados — que es exactamente el fallback actual de Profiles.
