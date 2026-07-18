package com.ecosentinel.appblocker.sync

import android.content.Context
import android.os.Build
import com.ecosentinel.appblocker.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class PairingApi(context: Context) {

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tokenStore = DeviceTokenStore(appContext)

    fun startPairing(): PairingActionResult {
        return try {
            val payload = PairingStartRequest(
                deviceToken = tokenStore.getOrCreateDeviceToken(),
                deviceName = defaultDeviceName()
            )
            val json = moshi.adapter(PairingStartRequest::class.java).toJson(payload)
            val request = Request.Builder()
                .url("${baseUrl()}/api/v1/pairing/start")
                .post(json.toRequestBody(JSON_MEDIA))
                .build()
            val responseBody = execute(request)
            val response = moshi.adapter(PairingStartResponse::class.java).fromJson(responseBody)
                ?: return PairingActionResult(false, "Pairing response is empty")
            tokenStore.savePendingPairing(response.pairingCode, response.expiresAt)
            PairingActionResult(true, "Pairing code: ${response.pairingCode}")
        } catch (e: Exception) {
            PairingActionResult(false, e.message ?: "Pairing failed")
        }
    }

    fun checkPairingStatus(): PairingActionResult {
        return try {
            val pairingCode = tokenStore.getPendingPairingCode()
                ?: return PairingActionResult(false, "Create a pairing code first")
            val deviceToken = tokenStore.getOrCreateDeviceToken()
            val request = Request.Builder()
                .url(
                    "${baseUrl()}/api/v1/pairing/status" +
                        "?pairingCode=${encode(pairingCode)}" +
                        "&deviceToken=${encode(deviceToken)}"
                )
                .get()
                .build()
            val responseBody = execute(request)
            val response = moshi.adapter(PairingStatusResponse::class.java).fromJson(responseBody)
                ?: return PairingActionResult(false, "Pairing response is empty")
            when (response.status.uppercase()) {
                "PAIRED" -> {
                    val deviceId = response.deviceId
                    val deviceSecret = response.deviceSecret
                    if (deviceId == null || deviceSecret.isNullOrBlank()) {
                        PairingActionResult(false, "Pairing response is incomplete")
                    } else {
                        tokenStore.savePairedDevice(deviceId.toString(), deviceSecret)
                        PairingActionResult(true, "Device paired")
                    }
                }
                "PENDING" -> PairingActionResult(false, "Waiting for dashboard confirmation")
                "EXPIRED" -> {
                    tokenStore.clearPendingPairing()
                    PairingActionResult(false, "Pairing code expired")
                }
                else -> PairingActionResult(false, "Pairing session not found")
            }
        } catch (e: Exception) {
            PairingActionResult(false, e.message ?: "Pairing status failed")
        }
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(body.ifBlank { "HTTP ${response.code}" })
            }
            return body
        }
    }

    private fun baseUrl(): String = tokenStore.getApiBaseUrl(BuildConfig.API_BASE_URL)

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "Android Device" }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    data class PairingStartRequest(
        val deviceToken: String,
        val deviceName: String
    )

    data class PairingStartResponse(
        val pairingCode: String,
        val expiresAt: String?,
        val status: String
    )

    data class PairingStatusResponse(
        val status: String,
        val pairingCode: String?,
        val expiresAt: String?,
        val deviceId: Int?,
        val deviceSecret: String?
    )

    data class PairingActionResult(
        val success: Boolean,
        val message: String
    )

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
