package com.panoli707.aetherio.nativeapp.ui

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/** A full quiet interval after a gesture; becoming hidden cancels both the timer and an animation. */
internal suspend fun runHomeAutoAdvance(eligible: Flow<Boolean>, pagerBusy: () -> Boolean, advance: suspend () -> Unit) {
    eligible.collectLatest { ready ->
        if (ready) while (currentCoroutineContext().isActive) {
            delay(8_000)
            if (!pagerBusy()) advance()
        }
    }
}
