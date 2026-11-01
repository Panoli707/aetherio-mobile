package com.panoli707.aetherio.nativeapp.data

enum class UpdateAction { CHECK, DOWNLOAD, INSTALL, CANCEL, DISMISS }

data class UpdateState(
    val available: AppUpdate? = null,
    val automatic: Boolean = false,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val verifying: Boolean = false,
    val waiting: Boolean = false,
    val progress: Int = 0,
    val ready: Boolean = false,
    val noticeVisible: Boolean = false,
    val installRequested: Boolean = false,
    val error: String? = null,
    val message: String? = null
)
