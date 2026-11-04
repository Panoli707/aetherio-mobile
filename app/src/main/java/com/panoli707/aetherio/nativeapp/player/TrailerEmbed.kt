package com.panoli707.aetherio.nativeapp.player

/** YouTube's official IFrame player; no watch-page scraping or direct-stream extraction. */
internal object TrailerEmbed {
    const val ORIGIN = "https://com.panoli707.aetherio.nativeapp"
    const val BASE_URL = "$ORIGIN/trailer/"
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    fun validId(value: String?): String? = value?.takeIf(videoId::matches)
    fun allowedMainFrame(url: String): Boolean = url == BASE_URL || url == "about:blank"
    fun error(code: Int): String = when (code) {
        100 -> "Este tráiler ya no está disponible o es privado."
        101, 150 -> "El propietario no permite reproducir este tráiler dentro de otras aplicaciones."
        153 -> "YouTube no pudo verificar el reproductor integrado. Inténtalo de nuevo."
        else -> "No se pudo reproducir el tráiler. Comprueba la conexión o prueba otro vídeo."
    }

    fun document(id: String): String {
        require(validId(id) != null)
        return """
            <!doctype html><html lang="es"><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' https://www.youtube.com; frame-src https://www.youtube.com https://www.youtube-nocookie.com; style-src 'unsafe-inline';">
            <style>html,body,#player{width:100%;height:100%;margin:0;background:#000;overflow:hidden}</style>
            </head><body><div id="player"></div><script>
            function onYouTubeIframeAPIReady(){
              window.aetherioPlayer=new YT.Player('player',{
                width:'100%',height:'100%',videoId:'$id',
                playerVars:{origin:'$ORIGIN',autoplay:1,controls:1,fs:1,playsinline:1,hl:'es'},
                events:{onReady:function(){console.log('AETHERIO_TRAILER_READY')},
                  onError:function(e){console.log('AETHERIO_TRAILER_ERROR:'+e.data)},
                  onStateChange:function(e){console.log('AETHERIO_TRAILER_STATE:'+e.data)}}
              });
            }
            function pauseTrailer(){if(window.aetherioPlayer&&window.aetherioPlayer.pauseVideo)window.aetherioPlayer.pauseVideo()}
            document.addEventListener('visibilitychange',function(){if(document.hidden)pauseTrailer()});
            window.addEventListener('pagehide',pauseTrailer);
            </script><script src="https://www.youtube.com/iframe_api"></script></body></html>
        """.trimIndent()
    }
}
