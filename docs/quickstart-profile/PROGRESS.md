# Quick Start — flujo de creación de perfil

## Qué se rehizo

El paso de creación de perfil de Quick Start se rehizo como una page completa con el
fondo compartido de Profiles, en lugar de una tarjeta dentro del wizard:

- `src/components/profile/ProfileBackdrop.tsx` (+ `.css`) — fondo compartido. Sin
  avatar son solo degradados; con avatar, esa imagen sale borrosa al fondo.
- `src/components/quickstart/NameKeyboard.tsx` (+ `.css`) — teclado `abc` / `ABC` /
  `123`, ESPACIO, `⌫` y BORRAR. Acepta teclado físico, ratón y foco espacial.
- `src/components/quickstart/QuickStartProfile.tsx` (+ `.css`) — etapas
  `name` → `image` → `summary` → `welcome`.
- `src/pages/QuickStart.tsx` — el paso `profile` y el paso `welcome` renderizan
  `QuickStartProfile`; `profileOnly` (ruta `/quick-start/profile`) usa el mismo sub-flujo.
- `src/App.tsx` — el arranque ya no le roba la navegación a ese flujo.

## Pantallas y medidas (viewport 1920)

Medido en el navegador contra lo registrado en `ANALYSIS.md`:

| Pantalla | Medida viva | Referencia | Resultado |
| --- | --- | --- | --- |
| Teclado, columna izquierda | `left 15.9vw`, `width 22.5vw` | ~`16.3vw`, `22.75vw` | dentro de tolerancia |
| Teclado, formulario | centro `70.2vw` | ~`69.7vw` | dentro de tolerancia |
| Imagen: título | centrado en `50vw` | centrado | ok |
| Imagen: 4 skeletons + 1 propia | 5 burbujas, la propia al final | 4 skeletons + propia | ok |
| Resumen: tarjeta derecha | presente | presente | ok |
| Bienvenida: título | `font 3.2vw` (61.44px) | `3.2vw` | exacto |
| Bienvenida: párrafo | `47.0vw` de medida | `47vw` | exacto |
| Bienvenida: CTA | `24.5vw` | ~`26.6vw` | dentro de tolerancia |

## Defectos encontrados y corregidos en esta pasada

1. **La ruta `/quick-start/profile` se saltaba la bienvenida.** `createProfileAndContinue`
   completaba y navegaba antes de `setProfileStage("welcome")`, así que el perfil se creaba
   y la persona iba directa a `/profiles` sin ver la última pantalla. Ahora la bienvenida es la
   última etapa del sub-flujo y su CTA es quien sale (`leaveProfileFlow`).
2. **Riesgo de perfil duplicado.** El CTA de bienvenida compartía handler con el de
   creación. Ahora `onSubmit` va a `leaveProfileFlow()` cuando la pantalla pintada es la
   bienvenida, nunca a `createProfileAndContinue()`.
3. **El arranque secuestraba la pantalla.** Crear un perfil cambia el perfil activo, lo
   que vuelve a disparar el efecto de arranque de `App.tsx`, y su `navigate(profileSelectionPath)`
   final se comía la bienvenida. Se salta esa navegación cuando la ruta es
   `/quick-start/profile`, igual que ya hacía la guarda de "sin perfiles" de ese mismo efecto.
4. **Doble pulsación de OK.** `leaveProfileFlow` lleva un cerrojo (`leavingProfileFlowRef`)
   para que el mando no salte un paso entero del wizard.
5. **Etiqueta de la burbuja propia.** "Añadir imagen propia" se pegaba a los bordes del
   círculo (13px de aire). Ahora se parte en dos líneas con `max-width: 64%` y ~39px de aire.

## Mando

El flujo se usa **con el mando**, no solo con ratón y teclado. Hacían falta dos
piezas que no estaban:

1. **El polling del mando no estaba montado.** `App.tsx` renderizaba
   `/quick-start/profile` pelado, sin `GamepadWakeListener` — que es quien llama a
   `useGamepad`, el único que hace polling de `navigator.getGamepads()`. Sin él no
   había D-pad, ni stick, ni A, ni B: la page era inutilizable con el mando. Ahora la
   rama va envuelta en `PartyProvider` + `GamepadWakeListener`, igual que `/profiles`.
2. **El motor espacial estaba apagado.** `spatialNav.ts` solo atiende las flechas en
   modo Big Picture o con la clase `aetherio-profile-selection` en `<html>`, que solo
   ponía `ProfileSelection`. El flujo enciende la misma clase al montar (no lleva CSS:
   es un flag de modo), así que las flechas mueven el foco por geometría.

Verificado con el mando simulado (teclas que sintetiza el hook):

- flechas → `a` → `b` → `h` → `g`, con la marca `.spatial-focus`;
- A (Enter sintético) escribe la tecla enfocada y pulsa los botones;
- B vuelve al resumen desde el pad del PIN, y el PIN sigue puesto.

## Avatares por defecto

"Elige una imagen" ya no muestra esqueletos: son las tres mascotas de
`src/assets/mascotas/` (copiadas de `F:\aetherio img\mascotas`), seguidas de la
burbuja de imagen propia. Siempre son **cuatro** burbujas, centradas:

- sin nada elegido → 3 mascotas + "Añadir imagen propia";
- con mascota elegida → la elegida con anillo blanco, más la de imagen propia
  (pulsarla otra vez la quita);
- con imagen propia → 3 mascotas + la vista previa, que al pulsarse quita la imagen.

La fila se centra con flex (`justify-content: center`) en vez de con las seis
columnas fijas de antes, que dejaban las cuatro burbujas pegadas a la izquierda.
El diámetro se mantiene en `clamp(110px, 11.2vw, 215px)`, el mismo que daba la
retícula de 6 columnas a 1920.

`localProfiles.normalizeAvatar` descarta todo lo que no empiece por `data:image/`,
así que la mascota se convierte antes de guardarse: se recorta a un cuadrado de
384px y se codifica en **WebP con alfa** (25 KB por avatar; el PNG equivalente
serían 175 KB y el original ~1,3 MB de base64 por perfil). Si el navegador no
trae WebP se cae a PNG. El recorte es cuadrado y centrado, como el `object-fit:
cover` de la burbuja.

## Cursor con el mando

`src/hooks/useHideCursorOnGamepad.ts`: al mover el mando se esconde el cursor
(`.aetherio-bp-hide-cursor`) y vuelve con el ratón o con una tecla real. Las teclas
que sintetiza el propio hook del mando se ignoran (`isTrusted === false`) o el
primer stick de la sesión traería el puntero de vuelta. Es la regla que ya usaba
ProfileSelection, extraída a hook; aquí solo se comparte el cursor, porque el flujo
de creación no cambia el modo de la app.

## Ajustes finos pedidos

1. **Las pestañas abc / ABC / 123 solo se cambian con LB y RB.** El tablist va con
   `data-spatial-ignore`, que el motor espacial ya respetaba
   (`spatialNav.ts:479`), así que las flechas ya no lo recorren; LB / RB ciclan el
   modo. Ratón y Tab del teclado siguen funcionando igual.
2. **Fuera el candado de la fila PIN** del resumen (era un `Lock` de lucide).
3. **Sin subrayado en "Qué se guarda en este equipo"** al foco: el foco se
   reconoce por el color (`rgba(255,255,255,0.46)` → `#fff`), no por la línea.
4. **B entre pasos del sub-flujo:** resumen → imagen, imagen → nombre, y en el
   nombre se sale del flujo al selector de perfiles. Al volver, el foco cae en la
   burbuja de **"Agregar"**, que es de donde se vino: `App` navega con
   `state: { focusAdd: true }` y `ProfileSelection` lo consume una vez al enfocar.
   **Solo si ya hay algún perfil**: en instalación nueva (0 perfiles) no hay
   selector al que volver, así que B no hace nada en vez de dejar en una pantalla
   vacía. En la bienvenida no se retrocede porque el perfil ya está creado y
   volver al resumen dejaría un OK que crearía otro.

Verificado en los dos casos: con 5 perfiles, B → `/profiles` con el foco en la
tarjeta "Agregar" y sin crear nada; en un contexto aislado con 0 perfiles, B
deja la pantalla de nombre intacta.

## Orden de los pasos

`nombre → PIN → imagen → resumen → bienvenida`

Poner el PIN es un paso propio, detrás del nombre. Al completar los cuatro
dígitos se queda puesto y sigue a la imagen; abajo está **Saltar** para pasar sin
él. Tocar la fila PIN del resumen sigue abriendo el mismo pad, pero como
`pin-edit` (una vista aparte): ahí no hay Saltar, se cierra con B y aparece
Quitar PIN si ya había uno.

Con B se retrocede paso a paso: `resumen → imagen → PIN → nombre`, y en el nombre
se sale al selector de perfiles.

## Transiciones entre pasos

Fades de verdad, no un salto: primero se desvanece el contenido (130ms), luego se
cambia y entra (220ms), con la Web Animations API sobre el contenedor del paso.
Es el mismo intercambio que hace Profiles entre las burbujas y el passcode. Antes
solo se animaba el `<h1>` y el resto de la pantalla aparecía de golpe.

Con `prefers-reduced-motion` el cambio es inmediato.

## Pantalla de PIN

La fila PIN del resumen abre **la misma pantalla que la de acceso de Profiles** (el
mismo `PinPad`: su título, su subtítulo, las cuatro casillas y el teclado), con los
textos de "ponerse" en vez de los de "entrar":

- `Elige un PIN` — encadena con *Elige un nombre* / *Elige una imagen*.
- `Cuatro dígitos para proteger este perfil en este equipo.`

Sin botón Guardar: al completar los cuatro dígitos el PIN queda puesto y se vuelve al
resumen. `Quitar PIN` aparece solo cuando ya hay un PIN puesto, para poder deshacerlo.
B / Esc también vuelve al resumen.

## Defectos de la pantalla de PIN (arreglados)

1. **B no volvía al selector de perfiles.** El handler de Escape de `ProfileSelection`
   hacía `setPinModal(null)`, que vacía el modal pero deja `showPin === true`: el pad
   seguía montado y vacío, así que el B no parecía hacer nada. Ahora hace `closePin()`
   (el intercambio completo: sale el pad y vuelven las burbujas).
2. **El foco se perdía al volver.** Se enfocaba el primer control de la lista. Ahora
   vuelve a **la burbuja desde la que se abrió el passcode** (`profileCardRefs`), y si
   no, al primer control como antes.
3. **El fade de vuelta estaba muerto.** `closePin` hacía el `gsap.fromTo` de la grilla
   cuando `showPin` todavía era `true`, cuando la grilla aún no está montada y el ref
   apuntaba a `null`. El fade ahora vive en un efecto sobre `showPin`, que es su
   espejo del efecto de entrada del pad.
4. **Doble B durante el fade.** `closePin` lleva `closingPinRef`, que se rearma al
   abrir, para no repetir el intercambio.
5. **El pad se cerraba solo al reabrirlo con un PIN ya puesto.** `PinPad` auto-confirma
   cuando `value.length === length`, así que montado con 4 dígitos disparaba
   `onComplete` en el primer render y la pantalla se cerraba al instante. Ahora solo
   confirma al **pasar de `length - 1` a `length`**, que es el gesto de escribir el
   último dígito. En Profiles y Ajustes el pad siempre se abre vacío, así que su
   comportamiento no cambia.

Verificado en vivo (perfil "F", PIN 1234): al abrir el PIN el foco va al primer
dígito; con B el pad desaparece, vuelve la grilla, se sigue en `/profiles` (el
`preventDefault` sigue bloqueando el back global) y el foco queda en la burbuja "F",
que es la segunda de la lista — no en la primera.



Flujo completo en `http://localhost:5199/quick-start/profile`:

- teclado: escribir, borrar con BORRAR, cambiar de `abc` a `ABC` y a `123`;
- `Continuar` bloqueado sin nombre;
- 4 skeletons + burbuja de imagen propia al final;
- resumen con las filas Nombre / Imagen / PIN;
- PIN: 4 dígitos exactos, puntos blancos dentro de las casillas, Guardar y Quitar PIN;
- al pulsar `OK` se crea el perfil y aparece la bienvenida;
- `Comenzar a ver` sale a `/profiles` y deja **un solo** perfil creado.

Consola: sin errores de React ni del flujo. Los avisos de GSAP (`.startup-copy`), Discord
(`invoke` sin Tauri) y `ERR_CONNECTION_REFUSED` (servidor de pósters) son preexistentes del
entorno, no de este flujo.

## Pendiente

- **Diff 1:1 contra las referencias:** las cinco imágenes del enunciado no están disponibles
  como archivo en esta sesión, así que no se pudo hacer comparación pixel a pixel. Las medidas
  de arriba salen de `ANALYSIS.md`, que se escribió a ojo y mezcla coordenadas desde arriba y
  desde abajo en algunas filas. Para un pase 1:1 hay que volver a adjuntar las referencias.
- Revisión adversarial en dos agentes y notas funcionales, tal como pide el skill.
- Los perfiles de prueba `F`, `qa` y `zed` quedaron en el selector; borrar si se quiere.
- Sin build de release: solo se validó en Vite.

## Deshacer un PIN

Con la pantalla de PIN ya en pie, el "atrás" quedó arreglado en los dos sitios donde
aparece (el passcode de Profiles y el de Quick Start). No queda ningún otro sitio con
el mismo pad: Ajustes lo usa para cambiar el PIN de un perfil y ahí no hay a dónde
volver.
