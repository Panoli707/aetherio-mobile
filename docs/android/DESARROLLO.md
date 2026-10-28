# Preparar el desarrollo Android desde Windows

Guia practica para principiantes: instalar herramientas y probar la interfaz web
del fork. No genera un APK ni instala una app Android. No cubre iOS, firma ni
publicacion en Play Store. El [README](../../README.md) describe el fork y su estado;
la [arquitectura Android](ARQUITECTURA.md) explica las piezas y sus limites.

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

Si ya lo clonaste, entra en su carpeta y ejecuta solo `git remote -v`.
`origin` debe identificar tu fork; `upstream`, el proyecto original de tu amigo.
Si clonaste con GitHub CLI (`gh`), puede haber creado `upstream` automaticamente.
Solo si falta ese remoto, anadelo:

```powershell
git remote add upstream https://github.com/trklll/aetherio.git
git remote -v
```

No cambies remotos a ciegas. Ejecuta los siguientes pasos desde la raiz del repo.

## 4. Preparar el SDK inicial y localizar Java 17

Abre Android Studio y completa el asistente inicial para instalar el SDK.
En SDK Manager consulta la ubicacion llamada Android SDK Location.
La ruta habitual es `$env:LOCALAPPDATA\Android\Sdk`; debe existir antes del script.
No basta con instalar Android Studio sin completar la descarga inicial del SDK.

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
- Instala `platform-tools`, Android API 35, Build Tools `35.0.0` y NDK `28.2.13676358`.
- Con `-AcceptLicenses`, acepta las licencias del SDK por solicitud tuya.
- Establece `JAVA_HOME`, `ANDROID_HOME` y `NDK_HOME` en el entorno del usuario.
- Anade al PATH del usuario `$HOME\.cargo\bin`, `platform-tools` y `cmdline-tools\19.0\bin` del SDK.
- No anade el directorio `bin` de Java al PATH y no genera un proyecto Android.

Comprueba `java` y `javac` 17, normaliza las rutas absolutas y valida los metadatos
de las versiones fijadas. Anadir entradas al PATH no elimina otras ni garantiza
prioridad sobre un SDK anterior: usa las rutas completas en caso de conflicto.

Puede descargar varios GB y tardar; conserva la terminal abierta hasta que termine.
Si has decidido aceptar las licencias, usa la variable `$jdk17` del paso anterior:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-android-sdk.ps1 -JavaHome "$jdk17" -AcceptLicenses
```

`Bypass` se limita a ese proceso: no cambia la politica global de PowerShell.
Para un SDK en otra ubicacion, anade `-SdkRoot "RUTA_REAL_DEL_SDK"` al comando.
Esa carpeta debe existir; no pegues literalmente el marcador de ruta.
Sin `-SdkRoot`, usa `$env:LOCALAPPDATA\Android\Sdk`.

Si prefieres revisar las licencias interactivamente, omite `-AcceptLicenses`.
Si se bloquea por licencias, ejecuta el gestor instalado con Java 17 y vuelve a
ejecutar el preparador despues de revisarlas:

```powershell
$env:JAVA_HOME = $jdk17
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\19.0\bin\sdkmanager.bat" --licenses
```

Adapta esa ruta si usas otro SDK. No ignores los errores. Se fija la version 19.0
porque `latest` ya deriva hacia otra CLI y no es un contrato reproducible.

Con un Android Studio mas nuevo puede aparecer un aviso de XML de SDK version 4
frente a version 3. En la preparacion comprobada no impidio instalar los paquetes;
comprueba igualmente el codigo de salida y las validaciones finales del script.

Tras completarlo, cierra la terminal y abre una nueva; reinicia el editor si hace
falta. Vuelve a entrar en `C:\ruta\aetherio-mobile` y comprueba:

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

`aarch64-linux-android` prepara ARM64; `x86_64-linux-android`, un emulador x86_64.
No instalan emuladores: prepara su imagen aparte en Device Manager de Android Studio.
Gradle usa `JAVA_HOME` en esta preparacion, aunque `java --version` en el PATH
muestre otro Java. No confundas ambos comandos ni cambies el PATH global a ciegas.

## 6. Instalar dependencias y compilar solo el frontend

En la raiz del repositorio, ejecuta:
```powershell
npm.cmd ci --ignore-scripts --no-audit --no-fund
npm.cmd run build
```

`ci` usa el lockfile y reemplaza `node_modules` si existe.
`--ignore-scripts` deshabilita los scripts de instalacion de dependencias;
algunos componentes nativos secundarios pueden quedar sin preparar. Este camino
no equivale a instalar todos los servicios ni el backend nativo de escritorio.

`run build` ejecuta explicitamente `prebuild`, despues TypeScript y Vite.
El resultado web se escribe en `dist/`: no es una app Windows ni un APK.
Los pasos `prebuild` y `predev` sincronizan shaders y generan el runtime de proveedores.
Pueden modificar `public/extension-typescript-deps.js`, que esta versionado.
Revisa antes de compartir cambios:

```powershell
git status --short
git diff -- public/extension-typescript-deps.js
```

No publiques regeneraciones incidentales ni descartes cambios previos tuyos o ajenos.

## 7. Abrir la interfaz y ejecutar las pruebas adecuadas

```powershell
npm.cmd run dev
```

Abre la direccion local que indique Vite, normalmente `http://localhost:1420`.
El proceso permanece activo; detenlo con `Ctrl+C` antes de continuar en esa terminal.
Esto prueba el frontend en el navegador, no Tauri Windows ni una app Android nativa.
Las funciones que requieren comandos Rust pueden fallar o no estar disponibles;
ver una pantalla no acredita reproduccion general, catalogos completos ni cuentas.

Ejecuta estas comprobaciones por separado desde la raiz:
```powershell
npm.cmd exec -- tsc --noEmit
npm.cmd exec -- vitest run --dir src --exclude "**/entityRanking.test.ts"
node --test src/pages/Entity/entityRanking.test.ts
```

La primera comprueba tipos; la segunda limita Vitest a `src`.
`entityRanking.test.ts` usa `node:test`, no Vitest: se excluye y se prueba con Node
por separado. Vitest sin ese alcance puede mezclar pruebas de `vendor` y
`website`, incluidas las de Playwright, y producir errores ajenos a esta seleccion.
Estos comandos no prueban el backend Rust, un dispositivo ni una compilacion Android.

Si una prueba agota el tiempo mientras hay instalaciones u otras tareas pesadas,
repite sin esa carga o limita los procesos de Vitest, sin ocultar fallos:

```powershell
npm.cmd exec -- vitest run --dir src --exclude "**/entityRanking.test.ts" --maxWorkers=2
```

## 8. Limites Android y comprobacion final

`npm.cmd exec -- tauri info` puede servir para diagnosticar herramientas Windows;
no acredita que Android compile. Aun no existe `src-tauri/gen/android` ni un APK.
Siguen pendientes el backend mpv Windows de `lib.rs`, el servicio de posters con
Node y la adaptacion y validacion de TLS del motor torrent, entre otros trabajos.
No ejecutes `tauri android init`, `dev` o `build` como si ya estuvieran habilitados:
son pasos futuros bloqueados por la adaptacion nativa y su validacion.

Para un futuro desarrollo Android, `TAURI_DEV_HOST` permite configurar la direccion
del PC accesible al dispositivo y HMR (recarga de cambios). Vite usa el puerto
1420 y HMR el 1421. No hace falta configurarlo para esta prueba local de navegador.
Si entonces falta acceso, revisa la red y permite solo lo necesario en el firewall
para redes privadas; no lo desactives globalmente ni abras acceso publico.

No publiques `.env`, tokens, credenciales, keystores ni contrasenas; revisa logs y diffs.
El workflow heredado `.github/workflows/release.yml` publica Windows, no Android.
No lo ejecutes para obtener un APK ni reutilices las claves de firma del original.

Al terminar, los resultados esperados son herramientas detectables, Java 17 por
`JAVA_HOME`, SDK y targets preparados, build web sin errores y pruebas seleccionadas
sin fallos. La interfaz debe poder abrirse en el navegador con los limites descritos.
Si algun paso falla, anota comando y error sin secretos: esta guia no certifica
el estado de un equipo concreto ni promete un APK funcional.
