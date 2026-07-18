package com.ecosentinel.appblocker.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class DeviceTokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrCreateDeviceToken(): String {
        val existing = prefs.getString(KEY_DEVICE_TOKEN, null)
        if (existing != null) return existing
        val token = UUID.randomUUID().toString().replace("-", "").lowercase()
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
        return token
    }

    fun getPairingCode(): String {
        val pendingCode = getPendingPairingCode()
        if (pendingCode != null) return pendingCode
        val token = getOrCreateDeviceToken().take(8)
        return token.uppercase().chunked(4).joinToString("-")
    }

    fun getDeviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)

    fun saveRemoteDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_REMOTE_DEVICE_ID, deviceId).apply()
    }

    fun getRemoteDeviceId(): String? = prefs.getString(KEY_REMOTE_DEVICE_ID, null)

    fun saveDeviceSecret(deviceSecret: String) {
        prefs.edit().putString(KEY_DEVICE_SECRET, deviceSecret).apply()
    }

    fun getDeviceSecret(): String? = prefs.getString(KEY_DEVICE_SECRET, null)

    fun isPaired(): Boolean {
        return !getRemoteDeviceId().isNullOrBlank() && !getDeviceSecret().isNullOrBlank()
    }

    fun saveApiBaseUrl(baseUrl: String) {
        val normalized = normalizeBaseUrl(baseUrl)
        val editor = prefs.edit()
        if (normalized.isBlank()) {
            editor.remove(KEY_API_BASE_URL)
        } else {
            editor.putString(KEY_API_BASE_URL, normalized)
        }
        editor.apply()
    }

    fun getApiBaseUrl(defaultBaseUrl: String): String {
        val stored = prefs.getString(KEY_API_BASE_URL, null)?.let(::normalizeBaseUrl)
        return if (stored.isNullOrBlank()) normalizeBaseUrl(defaultBaseUrl) else stored
    }

    fun savePendingPairing(pairingCode: String, expiresAt: String?) {
        prefs.edit()
            .putString(KEY_PENDING_PAIRING_CODE, pairingCode)
            .putString(KEY_PENDING_PAIRING_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun clearPendingPairing() {
        prefs.edit()
            .remove(KEY_PENDING_PAIRING_CODE)
            .remove(KEY_PENDING_PAIRING_EXPIRES_AT)
            .apply()
    }

    fun getPendingPairingCode(): String? = prefs.getString(KEY_PENDING_PAIRING_CODE, null)

    fun getPendingPairingExpiresAt(): String? = prefs.getString(KEY_PENDING_PAIRING_EXPIRES_AT, null)

    fun savePairedDevice(deviceId: String, deviceSecret: String) {
        prefs.edit()
            .putString(KEY_REMOTE_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_SECRET, deviceSecret)
            .remove(KEY_PENDING_PAIRING_CODE)
            .remove(KEY_PENDING_PAIRING_EXPIRES_AT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "device_token_store"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_REMOTE_DEVICE_ID = "remote_device_id"
        private const val KEY_DEVICE_SECRET = "device_secret"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_PENDING_PAIRING_CODE = "pending_pairing_code"
        private const val KEY_PENDING_PAIRING_EXPIRES_AT = "pending_pairing_expires_at"

        private fun normalizeBaseUrl(value: String): String {
            return value.trim().trimEnd('/')
        }
    }
}
