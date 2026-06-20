package com.ecosentinel.appblocker.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

class AppPasswordStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasPassword(): Boolean = !prefs.getString(KEY_HASH, null).isNullOrBlank()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val expectedHash = prefs.getString(KEY_HASH, null) ?: return false
        val salt = saltHex.hexToBytes()
        return hashPin(pin, salt) == expectedHash
    }

    fun clearPassword() {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .apply()
    }

    fun getUnlockMode(): PasswordUnlockMode {
        val value = prefs.getString(KEY_UNLOCK_MODE, null) ?: return PasswordUnlockMode.EVERY_LAUNCH
        return runCatching { PasswordUnlockMode.valueOf(value) }
            .getOrDefault(PasswordUnlockMode.EVERY_LAUNCH)
    }

    fun setUnlockMode(mode: PasswordUnlockMode) {
        prefs.edit()
            .putString(KEY_UNLOCK_MODE, mode.name)
            .apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    companion object {
        private const val PREFS_NAME = "app_password_store"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_UNLOCK_MODE = "unlock_mode"
    }
}
