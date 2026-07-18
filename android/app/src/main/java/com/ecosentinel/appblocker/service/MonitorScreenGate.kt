package com.ecosentinel.appblocker.service

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * Coordinates monitor-loop sleep while the screen is off and wake-up on [android.content.Intent.ACTION_SCREEN_ON]
 * / [android.content.Intent.ACTION_USER_PRESENT].
 */
object MonitorScreenGate {

    const val WAKE_DEBOUNCE_MS = 500L
    const val SCREEN_WAKE_FALLBACK_MS = 30 * 60_000L

    private val sleepSignal = Channel<Unit>(Channel.CONFLATED)
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    var screenInteractive: Boolean = true
        private set

    private var lastWakeSignalAtMillis: Long = 0L

    fun init(isInteractive: Boolean) {
        screenInteractive = isInteractive
    }

    fun notifyScreenOff() {
        screenInteractive = false
        sleepSignal.trySend(Unit)
    }

    fun notifyScreenWake() {
        val nowMillis = System.currentTimeMillis()
        screenInteractive = true
        if (nowMillis - lastWakeSignalAtMillis < WAKE_DEBOUNCE_MS) {
            return
        }
        lastWakeSignalAtMillis = nowMillis
        wakeSignal.trySend(Unit)
    }

    /**
     * @return `true` if the screen turned off before [delayMs] elapsed.
     */
    suspend fun awaitSleepOrDelay(delayMs: Long): Boolean {
        if (!screenInteractive) {
            return true
        }
        return select {
            sleepSignal.onReceive { true }
            onTimeout(delayMs) { false }
        }
    }

    suspend fun awaitWake() {
        wakeSignal.receive()
    }
}
