package com.ecosentinel.appblocker.modules.inapp

import java.util.concurrent.atomic.AtomicReference

object InAppFeatureState {

    private data class Snapshot(
        val packageName: String,
        val feature: InAppFeature,
        val updatedAtMillis: Long
    )

    private val current = AtomicReference<Snapshot?>(null)

    private const val MAX_AGE_MS = 15_000L

    fun update(packageName: String, feature: InAppFeature) {
        current.set(
            Snapshot(
                packageName = packageName,
                feature = feature,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    fun clearForPackage(packageName: String) {
        val snapshot = current.get() ?: return
        if (snapshot.packageName == packageName) {
            current.set(null)
        }
    }

    fun clear() {
        current.set(null)
    }

    fun currentFeature(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): InAppFeature? {
        val snapshot = current.get() ?: return null
        if (snapshot.packageName != packageName) {
            return null
        }
        if (nowMillis - snapshot.updatedAtMillis > MAX_AGE_MS) {
            return null
        }
        return snapshot.feature
    }
}
