package com.ecosentinel.appblocker.sync

import android.content.Context
import com.ecosentinel.appblocker.BuildConfig
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.engine.RuleLockMode
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SyncApi(context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tokenStore = DeviceTokenStore(context)
    private val database = AppDatabase.getInstance(context)
    private val usageTracker = UsageTracker(context)

    fun sync(): SyncResult {
        return try {
            pushUsage()
            pullPolicies()
            SyncResult(success = true, message = "Sync completed")
        } catch (e: Exception) {
            SyncResult(success = false, message = e.message ?: "Sync failed")
        }
    }

    private fun pushUsage() {
        val usageMap = kotlinx.coroutines.runBlocking { usageTracker.syncTodayUsage() }
        val payload = UsageSyncRequest(
            deviceToken = tokenStore.getOrCreateDeviceToken(),
            dateKey = usageTracker.todayKey(),
            entries = usageMap.map { (pkg, millis) ->
                UsageEntry(packageName = pkg, usedMillis = millis)
            }
        )
        val json = moshi.adapter(UsageSyncRequest::class.java).toJson(payload)
        val request = withDeviceSecret(Request.Builder())
            .url("${baseUrl()}/api/v1/devices/sync/usage")
            .post(json.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().close()
    }

    private fun pullPolicies() {
        val request = withDeviceSecret(Request.Builder())
            .url("${baseUrl()}/api/v1/devices/policies?deviceToken=${encode(tokenStore.getOrCreateDeviceToken())}")
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return
        }
        val body = response.body?.string().orEmpty()
        response.close()
        if (body.isBlank()) return

        val type = Types.newParameterizedType(List::class.java, RemotePolicy::class.java)
        val adapter = moshi.adapter<List<RemotePolicy>>(type)
        val remotePolicies = adapter.fromJson(body) ?: return
        val entities = remotePolicies.map { it.toEntity() }
        kotlinx.coroutines.runBlocking {
            val localCategoryRules = database.policyRuleDao().getAllRules()
                .filter { it.targetType == TargetType.CATEGORY }
            val localGroupRules = database.policyRuleDao().getAllRules()
                .filter { it.targetType == TargetType.CUSTOM_GROUP }
            val localWebsiteRules = database.policyRuleDao().getAllRules()
                .filter { it.targetType == TargetType.URL_PATTERN }
            database.policyRuleDao().deleteAll()
            database.policyRuleDao().upsertAll(
                entities + localCategoryRules + localGroupRules + localWebsiteRules
            )
        }
    }

    data class UsageSyncRequest(
        val deviceToken: String,
        val dateKey: String,
        val entries: List<UsageEntry>
    )

    data class UsageEntry(
        val packageName: String,
        val usedMillis: Long
    )

    data class RemotePolicy(
        val id: String,
        val moduleType: String,
        val targetType: String,
        val packageName: String?,
        val featureId: String?,
        val dailyLimitMinutes: Int?,
        val blockMode: String? = null,
        val enabled: Boolean,
        val scheduleJson: String?,
        val metadataJson: String?,
        val lockMode: String? = null,
        val lockUntilDayEndMillis: Long? = null,
        val lockUntilCustomMillis: Long? = null,
        val lockOnBlockActive: Boolean? = null,
        val lockDelayMinutes: Int? = null,
        val lockDelayStartedAtMillis: Long? = null
    ) {
        fun toEntity(): PolicyRuleEntity {
            return PolicyRuleEntity(
                id = id,
                moduleType = BlockModuleType.valueOf(moduleType),
                targetType = TargetType.valueOf(targetType),
                packageName = packageName,
                featureId = featureId,
                dailyLimitMinutes = dailyLimitMinutes,
                blockMode = blockMode?.let { BlockMode.valueOf(it) }
                    ?: if (dailyLimitMinutes == null) BlockMode.PERMANENT else BlockMode.TIME_LIMIT,
                enabled = enabled,
                scheduleJson = scheduleJson,
                metadataJson = metadataJson,
                lockMode = lockMode?.let { RuleLockMode.valueOf(it) } ?: RuleLockMode.NORMAL,
                lockUntilDayEndMillis = lockUntilDayEndMillis,
                lockUntilCustomMillis = lockUntilCustomMillis,
                lockOnBlockActive = lockOnBlockActive ?: false,
                lockDelayMinutes = lockDelayMinutes,
                lockDelayStartedAtMillis = lockDelayStartedAtMillis
            )
        }
    }

    data class SyncResult(val success: Boolean, val message: String)

    private fun baseUrl(): String = tokenStore.getApiBaseUrl(BuildConfig.API_BASE_URL)

    private fun withDeviceSecret(builder: Request.Builder): Request.Builder {
        val secret = tokenStore.getDeviceSecret()
        if (!secret.isNullOrBlank()) {
            builder.addHeader("X-Device-Secret", secret)
        }
        return builder
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
