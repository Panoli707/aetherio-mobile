package com.panoli707.aetherio.nativeapp.data

import coil.EventListener
import coil.decode.DataSource
import coil.decode.Decoder
import coil.decode.DecodeResult
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult
import coil.request.ErrorResult

internal class LoadImageTiming : EventListener {
    private var requestSpan: LoadTiming.Span? = null
    private var decode: LoadTiming.Span? = null
    override fun onStart(request: ImageRequest) { requestSpan = LoadTiming.start(LoadTiming.Stage.IMAGE_LOAD) }
    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decode = LoadTiming.start(LoadTiming.Stage.IMAGE_DECODE)
    }
    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) { decode?.finish(result != null) }
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        requestSpan?.finish()
        val stage = when (result.dataSource) {
            DataSource.MEMORY_CACHE -> LoadTiming.Stage.IMAGE_MEMORY
            DataSource.DISK -> LoadTiming.Stage.IMAGE_DISK
            DataSource.NETWORK -> LoadTiming.Stage.IMAGE_NETWORK
            else -> return
        }
        // Source stages count cache/network hits; IMAGE_LOAD measures the full request duration.
        LoadTiming.start(stage).finish()
    }
    override fun onError(request: ImageRequest, result: ErrorResult) { requestSpan?.finish(false) }
    override fun onCancel(request: ImageRequest) { requestSpan?.finish(false) }
}
