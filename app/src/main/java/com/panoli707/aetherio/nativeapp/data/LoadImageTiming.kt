package com.panoli707.aetherio.nativeapp.data

import coil.EventListener
import coil.decode.DataSource
import coil.decode.Decoder
import coil.decode.DecodeResult
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult

internal class LoadImageTiming : EventListener {
    private var decode: LoadTiming.Span? = null
    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decode = LoadTiming.start(LoadTiming.Stage.IMAGE_DECODE)
    }
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) { decode?.finish(result != null) }
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        val stage = when (result.dataSource) {
            DataSource.MEMORY_CACHE -> LoadTiming.Stage.IMAGE_MEMORY
            DataSource.DISK -> LoadTiming.Stage.IMAGE_DISK
            DataSource.NETWORK -> LoadTiming.Stage.IMAGE_NETWORK
            else -> return
        }
        LoadTiming.start(stage).finish()
    }
}
