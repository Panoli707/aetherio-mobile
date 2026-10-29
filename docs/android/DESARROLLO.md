# Preparar el desarrollo Android desde Windows

Guia para principiantes: preparar herramientas, comprobar el frontend, compilar
un APK debug e instalarlo para probar. No cubre iOS, firma de produccion ni
publicacion en Play Store. El [README](../../README.md) describe el fork y su estado;
la [arquitectura Android](ARQUITECTURA.md) explica las piezas y sus limites.

Ya existe un primer APK debug compilado para ARM64 y x86_64, con pruebas basicas en
un emulador Pixel 7. Los resultados estan en la seccion 9; no acreditan
compatibilidad con todos los telefonos ni soporte de reproduccion.

## 1. Antes de empezar

- Usa Windows de 64 bits, PowerShell y una cuenta que pueda autorizar instaladores.
- Necesitas Internet para las descargas y varios GB libres para SDK, NDK y herramientas.
- Reserva espacio adicional si despues preparas un emulador en Android Studio.
- Ejecuta los comandos uno por uno; si uno falla, revisa el error antes de seguir.
- No repitas una instalacion que ya tengas correctamente preparada.

Son instrucciones para tu equipo, no resultados verificados en el. No pegues prompts.
Las rutas son ejemplos: sustituye `C:\ruta` por una carpeta existente que elijas.
`$HOME` representa tu carpeta de usuario y `$env:LOCALAPPDATA` sus datos locales.

## 2. Instalar las herramientas

Abre PowerShell. Si falta `winget`, prepara el Instalador de aplicacion de Windows.
Autoriza cada instalador; no necesitas trabajar siempre como administrador.

Instala Git, Node.js LTS y Android Studio:

```powershell
winget install --id Git.Git --exact
winget install --id OpenJS.NodeJS.LTS --exact
winget install --id Google.AndroidStudio --exact
```

Instala las herramientas C++ de Microsoft para el toolchain Rust MSVC:
```powershell
winget install --id Microsoft.VisualStudio.2022.BuildTools --exact --override "--wait --quiet --norestart --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
```

Instala Rust con perfil minimo y host MSVC, y despues el JDK 17:
```powershell
winget install --id Rustlang.Rustup --exact --override "-y --profile minimal --default-host x86_64-pc-windows-msvc --default-toolchain stable"
winget install --id EclipseAdoptium.Temurin.17.JDK --exact
```

Espera a que terminen los instaladores y atiende un reinicio si lo solicitan.
Cierra PowerShell y abre una terminal nueva para cargar las variables y el PATH.
Si usas una terminal integrada, puede ser necesario reiniciar tambien su editor.

```powershell
git --version
node --version
npm.cmd --version
rustup show
cargo --version
```

Usa Node LTS reciente: una prueba de esta guia ejecuta TypeScript directamente
con Node. En PowerShell usamos `npm.cmd` para evitar el bloqueo de `npm.ps1`;
no cambies la politica global de ejecucion para resolverlo.

## 3. Obtener el fork y revisar los remotos

Desde una carpeta existente, clona el fork y entra en el repositorio:

```powershell
Set-Location "C:\ruta"
git clone https://github.com/Panoli707/aetherio-mobile.git
Set-Location "C:\ruta\aetherio-mobile"
git remote -v
```

Si ya lo clonaste, entra y ejecuta solo `git remote -v`. `origin` debe ser tu fork;
`upstream`, el proyecto original. `gh` puede haberlo creado; anadelo solo si falta:

```powershell
git remote add upstream https://github.com/trklll/aetherio.git
git remote -v
```

No cambies remotos a ciegas. Ejecuta los siguientes pasos desde la raiz del repo.

## 4. Preparar el SDK inicial y localizar Java 17

Completa el asistente inicial de Android Studio para descargar el SDK y consulta
Android SDK Location en SDK Manager. La ruta habitual es
`$env:LOCALAPPDATA\Android\Sdk`; debe existir antes de ejecutar el preparador.

Localiza el JDK real, sin inventar versiones; consulta la ubicacion habitual de Temurin:

```powershell
Get-ChildItem -LiteralPath "$env:ProgramFiles\Eclipse Adoptium" -Directory
```

Si lo instalaste en otro lugar, buscalo en el Explorador o el instalador.
Introduce la raiz del JDK 17, sin comillas adicionales; no `bin` ni `java.exe`:

```powershell
$jdk17 = Read-Host "Ruta completa de la carpeta del JDK 17"
& (Join-Path $jdk17 "bin\java.exe") --version
```

La salida debe indicar Java 17. Android Studio puede incluir otro Java y tu PATH
puede resolver Java 25: ninguno sustituye la comprobacion de esta ruta concreta.

## 5. Ejecutar el preparador del SDK

Lee [setup-android-sdk.ps1](../../scripts/setup-android-sdk.ps1) antes de ejecutarlo.
Requiere `-JavaHome` con un JDK 17 y una carpeta SDK ya existente.
No lo ejecutes simultaneamente con otra instalacion que modifique ese SDK.

El script realiza estas operaciones:

- Si faltan las Command-line Tools 19.0, consulta el XML oficial de Android, descarga esa version para Windows y valida su checksum antes de extraerlas.
- Instala `platform-tools`, Android API 36, Build Tools `35.0.0` y NDK `28.2.13676358` (r28c), sin borrar paquetes anteriores.
- Con `-AcceptLicenses`, acepta las licencias del SDK por solicitud tuya.
- Establece `JAVA_HOME`, `ANDROID_HOME` y `NDK_HOME` en el entorno del usuario.
- Anade al PATH del usuario `$HOME\.cargo\bin`, `platform-tools` y `cmdline-tools\19.0\bin` del SDK.
- No anade el directorio `bin` de Java al PATH y no genera un proyecto Android.

Comprueba `java`/`javac` 17, rutas absolutas y metadatos de versiones. Anadir al
PATH no elimina entradas ni garantiza prioridad: usa rutas completas si hay conflicto.

Puede descargar varios GB y tardar. Si aceptas las licencias, usa `$jdk17` del paso anterior:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-android-sdk.ps1 -JavaHome "$jdk17" -AcceptLicenses
```

`Bypass` solo afecta a ese proceso. Para otro SDK anade `-SdkRoot "RUTA_REAL_DEL_SDK"`
con una carpeta existente, no el marcador literal; por defecto usa
`$env:LOCALAPPDATA\Android\Sdk`.

Para revisar licencias interactivamente, omite `-AcceptLicenses`. Si se bloquea,
usa Java 17 y el gestor instalado; despues vuelve a ejecutar el preparador:

```powershell
$env:JAVA_HOME = $jdk17
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\19.0\bin\sdkmanager.bat" --licenses
```

Adapta esa ruta si usas otro SDK. No ignores los errores. Se fija la version 19.0
porque `latest` ya deriva hacia otra CLI y no es un contrato reproducible.

Un aviso de XML de SDK version 4 frente a 3 no impidio la preparacion comprobada;
revisa igualmente el codigo de salida y las validaciones finales del script.

Abre una terminal nueva (reinicia el editor si hace falta), entra en el repo y comprueba:

```powershell
$env:JAVA_HOME
& (Join-Path $env:JAVA_HOME "bin\java.exe") --version
$env:ANDROID_HOME
$env:NDK_HOME
& "$env:ANDROID_HOME\platform-tools\adb.exe" version
& "$env:ANDROID_HOME\cmdline-tools\19.0\bin\sdkmanager.bat" --version
rustup target add aarch64-linux-android x86_64-linux-android
rustup target list --installed
```

Los targets preparan ARM64 y x86_64; no instalan emuladores: prepara la imagen en
Device Manager de Android Studio. Gradle usa `JAVA_HOME` aunque el PATH muestre
otro Java; no cambies el PATH global a ciegas.

## 6. Instalar dependencias y compilar solo el frontend

En la raiz del repositorio, ejecuta:
```powershell
npm.cmd ci --ignore-scripts --no-audit --no-fund
npm.cmd run build
```

`ci` usa el lockfile y reemplaza `node_modules`. `--ignore-scripts` omite scripts
de instalacion de dependencias: no prepara todos los servicios de escritorio.

`run build` ejecuta `prebuild`, TypeScript y Vite; `dist/` no es un APK.
`prebuild`/`predev` sincronizan shaders y generan proveedores; pueden modificar
`public/extension-typescript-deps.js`, versionado. Revisa antes de compartir:

```powershell
git status --short
git diff -- public/extension-typescript-deps.js
```

No publiques regeneraciones incidentales ni descartes cambios previos tuyos o ajenos.

## 7. Abrir la interfaz y ejecutar las pruebas adecuadas

```powershell
npm.cmd run dev
```

Abre la direccion de Vite, normalmente `http://localhost:1420`; termina con `Ctrl+C`.
Esto prueba el navegador, no Android: faltan comandos Rust y no acredita reproduccion.

Ejecuta estas comprobaciones por separado desde la raiz:
```powershell
npm.cmd exec -- tsc --noEmit
npm.cmd exec -- vitest run --dir src --exclude "**/entityRanking.test.ts"
node --test src/pages/Entity/entityRanking.test.ts
```

La primera comprueba tipos. Vitest se limita a `src` para no mezclar `vendor`,
`website` o Playwright; `entityRanking.test.ts` usa `node:test` y se prueba aparte.
Pasaron 588 pruebas de Vitest y 4 de Node. Son pruebas del frontend; los resultados
de builds y ejecucion Android se describen por separado mas abajo.

Si una prueba agota el tiempo mientras hay instalaciones u otras tareas pesadas,
repite sin esa carga o limita los procesos de Vitest, sin ocultar fallos:

```powershell
npm.cmd exec -- vitest run --dir src --exclude "**/entityRanking.test.ts" --maxWorkers=2
```

## 8. Compilar un APK debug

Lee [android.ps1](../../scripts/android.ps1). Desde la raiz usa el proyecto ya
generado en `src-tauri/gen/android`: **no ejecutes `init` al clonar si ya existe**.
La accion `init` solo sirve para inicializar si falta; el script rechaza sobrescribir
la carpeta existente porque contiene ajustes propios.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\android.ps1 -Action build -Target aarch64
```

`build` usa `aarch64` por defecto. Para un emulador x86_64, repite con `-Target x86_64`.
Solo genera APK debug (`--debug --apk`), no AAB ni release. El script carga
`JAVA_HOME`, `ANDROID_HOME` y `NDK_HOME` de User en Process (usa Process si faltan
en User), prioriza Rust y Java en el PATH solo de ese proceso y fija
`CARGO_BUILD_JOBS=4` si no estaba definido. Evita el Java 25 de Oracle o el entorno
viejo del editor sin cambiar el PATH global. Llama a `npm run tauri -- android ...`
desde el repositorio; no hace falta ejecutar Gradle directamente.

La primera build descargo muchas dependencias y Gradle instalo API 36 automaticamente;
ahora el preparador ya la instala. El proyecto usa Gradle 8.14.3 y AGP 8.11.0,
`compileSdk`/`targetSdk` 36 y `minSdk` 24 (Android 7.0), no una matriz verificada.
Los hooks Android generan proveedores, TypeScript y Vite sin empaquetar mpv/Node.
La salida local ya existente es:

```text
src-tauri/gen/android/app/build/outputs/apk/universal/debug/app-universal-debug.apk
```

`universal` puede incluir varias ABI de builds anteriores: no significa "solo ARM64".
No subas el APK al repositorio; su carpeta `build` esta ignorada por Git.

## 9. Instalar y comprobar sin borrar datos

En tu telefono habilita opciones de desarrollador y depuracion USB; conecta el
cable y autoriza tu PC. O inicia un emulador con ABI compatible en Android Studio.
En una terminal nueva, desde la raiz, comprueba que `ANDROID_HOME` este cargado:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r ".\src-tauri\gen\android\app\build\outputs\apk\universal\debug\app-universal-debug.apk"
```

Continua solo con tu dispositivo en estado `device`, no `unauthorized`. Si hay varios,
anade `-s SERIAL` antes de `install`, usando el identificador que muestra `devices`.
`-r` reinstala/actualiza esta misma app conservando sus datos si la firma es compatible;
no desinstales ni borres datos para forzar una instalacion. Comprueba que sea tu
APK del identificador `com.panoli707.aetherio.mobile`, no otra aplicacion.

Android genera automaticamente la firma debug local con `debug.keystore`. No
publiques esa clave: no es firma de produccion y otro PC puede usar una distinta,
por lo que no se garantiza actualizar una instalacion anterior desde otro equipo.
Abre Aetherio Mobile desde su icono o, de forma opcional:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.panoli707.aetherio.mobile/.MainActivity
```

Prueba realizada el 28 de octubre de 2026: AVD Pixel 7, API 37, ABI x86_64,
viewport WebView de 411 px. Se uso CDP para interactuar con la WebView y ADB para
instalar, arrancar y enviar el boton Atras nativo.

- Arranque en frio de la app y seleccion del perfil local: correctos.
- Catalogos remotos y apertura de una ficha con datos reales: correctos.
- Crear un perfil con nombre, PIN omitido y avatar: correcto, sin desborde horizontal.
- Guardar un titulo en biblioteca y conservarlo al reinstalar con `-r`: correcto.
- El perfil creado seguia disponible tras reinstalar y arrancar la app.
- Panel de cuentas ausente en la biblioteca Android: comprobado.
- Atras nativo de Biblioteca a Inicio: comprobado sin listeners de diagnostico.

El AVD mostro un ANR de `com.android.systemui` que interceptaba el boton Atras;
tras pulsar Wait en ese aviso, el evento llego a la app. No era un ANR de Aetherio.
Faltan telefono ARM64, Android estable, rotacion, gestos, teclado del sistema,
red caida y persistencia ante muerte del proceso/reinicio completo del dispositivo.
El aviso "Reproduccion Android pendiente" es deliberado: no hay mpv ni Media3.
Cuentas/sincronizacion, proveedores, yt-dlp, SpatialPosters y Discord no se habilitan.

## 10. Desarrollo en vivo y diagnostico

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\android.ps1 -Action dev
```

`dev` llama a `tauri android dev`, con deteccion/seleccion de dispositivo de Tauri.
`-Target` solo selecciona ABI para `build`: no se transmite a `dev`; esa CLI no
admite `dev --target`. Detenlo con `Ctrl+C`. Si falla la conexion a Vite, revisa
`TAURI_DEV_HOST` (IP del PC accesible al movil), puertos 1420 y HMR 1421 y red privada;
no desactives el firewall ni abras acceso publico. `tauri info` ayuda a diagnosticar,
pero no demuestra ejecucion Android. Para errores usa `adb.exe logcat` mediante
la misma ruta completa; revisa los registros antes de compartirlos.

No publiques `.env`, tokens, credenciales, keystores ni contrasenas. El codigo y
esta documentacion pueden publicarse conforme avanza el port, no los artefactos
locales ni secretos. El workflow heredado `.github/workflows/release.yml` publica
Windows: no lo ejecutes para Android ni reutilices claves del original. Estos
pasos no crean releases automaticamente ni publican en Google Play.
