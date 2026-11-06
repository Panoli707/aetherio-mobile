# Fuentes del resolver de tráileres

Las versiones de biblioteca y ejecutable son independientes:

- `io.github.junkfood02.youtubedl-android:library:0.18.1` y `common:0.18.1`: runtime de Android, CPython 3.12.11 y QuickJS 2025-04-26. Los JAR de fuentes son los publicados en Maven Central. El ZIP conserva fuentes y scripts del tag upstream, excluyendo binarios precompilados; `provenance.json` registra ambos hashes y la transformación.
- `yt-dlp 2026.10.26`: ejecutable zipimport oficial, incluido como `app/src/main/res/raw/aetherio_ytdlp`. SHA256: `e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`. Se verifica al instalarlo desde el APK al almacenamiento privado. La versión incluida en el AAR upstream no se utiliza para reproducir.
- Las fuentes de GDBM 1.26, Readline 8.3 y QuickJS se incluyen para los componentes del runtime. El wrapper conserva sus instrucciones `BUILD_PYTHON.md` y demás scripts. Sus binarios son los de la dependencia Maven fijada; no se recompilan durante el build de Aetherio.
- `termux-runtime-recipes.zip` incluye recetas y parches del 24 de agosto de 2025, fecha de compilación identificada en el runtime. La procedencia y el commit están dentro del ZIP; esto no constituye una afirmación de reproducción bit a bit de los binarios upstream.

La adaptación Android es código nuevo: no se ha copiado el runtime Windows, su proxy local ni libmpv. El flujo de referencia se verificó en `src/hooks/useYouTubePlayer.ts` y `youtube_resolve_stream` de `src-tauri/src/lib.rs` del ZIP local de Aetherio PC.

No se activan actualizaciones de ejecutables ni descargas opcionales de componentes EJS. Python, QuickJS y los componentes EJS del extractor se distribuyen en el APK. La resolución sí consulta los datos y scripts del reproductor de YouTube; QuickJS los procesa localmente. Se resuelve solo el tráiler seleccionado, sin descargar medios a archivos ni acceder a cuentas.

Los textos de licencia están en `app/src/main/assets/licenses/`. La publicación adjunta `youtubedl-android-0.18.1-sources.zip`, que agrupa estos archivos y su procedencia, además del archivo de fuentes de yt-dlp. No incluir credenciales locales en esos archivos.
