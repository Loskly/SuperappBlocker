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
        val token = UUID.randomUUID().toString().replace("-", "").take(8).lowercase()
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
        return token
    }

    fun getPairingCode(): String {
        val token = getOrCreateDeviceToken()
        return token.uppercase().chunked(4).joinToString("-")
    }

    fun getDeviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)

    fun saveRemoteDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_REMOTE_DEVICE_ID, deviceId).apply()
    }

    fun getRemoteDeviceId(): String? = prefs.getString(KEY_REMOTE_DEVICE_ID, null)

    companion object {
        private const val PREFS_NAME = "device_token_store"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_REMOTE_DEVICE_ID = "remote_device_id"
    }
}
