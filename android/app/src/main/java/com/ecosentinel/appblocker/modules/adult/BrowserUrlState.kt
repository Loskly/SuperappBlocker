package com.ecosentinel.appblocker.modules.adult

import java.util.concurrent.atomic.AtomicReference

object BrowserUrlState {

    private data class Snapshot(
        val packageName: String,
        val url: String,
        val updatedAtMillis: Long
    )

    private val current = AtomicReference<Snapshot?>(null)

    private const val MAX_AGE_MS = 15_000L

    fun update(packageName: String, url: String) {
        val normalized = url.trim()
        if (normalized.isEmpty()) {
            return
        }
        current.set(
            Snapshot(
                packageName = packageName,
                url = normalized,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    fun currentUrl(packageName: String, nowMillis: Long = System.currentTimeMillis()): String? {
        val snapshot = current.get() ?: return null
        if (snapshot.packageName != packageName) {
            return null
        }
        if (nowMillis - snapshot.updatedAtMillis > MAX_AGE_MS) {
            return null
        }
        return snapshot.url
    }

    fun clear() {
        current.set(null)
    }
}
