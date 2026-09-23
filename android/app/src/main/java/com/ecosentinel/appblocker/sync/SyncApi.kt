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
import com.ecosentinel.appblocker.util.InstalledAppsHelper
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

    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val tokenStore = DeviceTokenStore(appContext)
    private val changeStore = DashboardChangeStore(appContext)
    private val featureControlBridge = RemoteFeatureControlBridge(appContext)
    private val database = AppDatabase.getInstance(appContext)
    private val usageTracker = UsageTracker(appContext)

    fun sync(): SyncResult {
        return try {
            pushUsage()
            val pulledPolicies = pullPolicies()
            val pulledFeatures = pullFeatureControls()
            val pushedState = pushDeviceState()
            val pulledChanges = pullDashboardChanges()
            SyncResult(
                success = true,
                message = "Sync completed: $pulledPolicies rules, $pulledFeatures features, ${pushedState.apps} apps, $pulledChanges changes"
            )
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

    private fun pullPolicies(): Int {
        val request = withDeviceSecret(Request.Builder())
            .url("${baseUrl()}/api/v1/devices/policies?deviceToken=${encode(tokenStore.getOrCreateDeviceToken())}")
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            throw IllegalStateException(body.ifBlank { "Policy sync HTTP ${response.code}" })
        }
        val body = response.body?.string().orEmpty()
        response.close()
        if (body.isBlank()) return 0

        val type = Types.newParameterizedType(List::class.java, RemotePolicy::class.java)
        val adapter = moshi.adapter<List<RemotePolicy>>(type)
        val remotePolicies = adapter.fromJson(body).orEmpty()
        val entities = remotePolicies.map { it.toEntity() }
        kotlinx.coroutines.runBlocking {
            database.policyRuleDao().upsertAll(entities)
        }
        return entities.size
    }

    private fun pushDeviceState(): StateSyncResponse {
        val installedApps = InstalledAppsHelper.getAllInstalledApps(appContext)
            .map { InstalledAppPayload(packageName = it.packageName, label = it.label) }
        val policies = kotlinx.coroutines.runBlocking {
            database.policyRuleDao().getAllRules().map { it.toRemotePolicy() }
        }
        val payload = DeviceStateSyncRequest(
            deviceToken = tokenStore.getOrCreateDeviceToken(),
            installedApps = installedApps,
            policies = policies,
            features = featureControlBridge.currentControls()
        )
        val json = moshi.adapter(DeviceStateSyncRequest::class.java).toJson(payload)
        val request = withDeviceSecret(Request.Builder())
            .url("${baseUrl()}/api/v1/devices/sync/state")
            .post(json.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throw IllegalStateException(body.ifBlank { "State sync HTTP ${response.code}" })
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return StateSyncResponse(apps = installedApps.size, policies = policies.size)
            }
            return moshi.adapter(StateSyncResponse::class.java).fromJson(body)
                ?: StateSyncResponse(apps = installedApps.size, policies = policies.size)
        }
    }

    private fun pullFeatureControls(): Int {
        val request = withDeviceSecret(Request.Builder())
            .url("${baseUrl()}/api/v1/devices/features?deviceToken=${encode(tokenStore.getOrCreateDeviceToken())}")
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            response.close()
            throw IllegalStateException(body.ifBlank { "Feature sync HTTP ${response.code}" })
        }
        val body = response.body?.string().orEmpty()
        response.close()
        if (body.isBlank()) return 0

        val type = Types.newParameterizedType(List::class.java, RemoteFeatureControl::class.java)
        val adapter = moshi.adapter<List<RemoteFeatureControl>>(type)
        val controls = adapter.fromJson(body).orEmpty()
        featureControlBridge.applyControls(controls)
        return controls.size
    }

    private fun pullDashboardChanges(): Int {
        val request = withDeviceSecret(Request.Builder())
            .url("${baseUrl()}/api/v1/devices/changes?deviceToken=${encode(tokenStore.getOrCreateDeviceToken())}")
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return 0
        }
        val body = response.body?.string().orEmpty()
        response.close()
        if (body.isBlank()) return 0

        val type = Types.newParameterizedType(
            List::class.java,
            DashboardChangeStore.DashboardChange::class.java
        )
        val adapter = moshi.adapter<List<DashboardChangeStore.DashboardChange>>(type)
        val changes = adapter.fromJson(body).orEmpty()
        changeStore.saveChanges(changes)
        return changes.size
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

    data class DeviceStateSyncRequest(
        val deviceToken: String,
        val installedApps: List<InstalledAppPayload>,
        val policies: List<RemotePolicy>,
        val features: List<RemoteFeatureControl>
    )

    data class InstalledAppPayload(
        val packageName: String,
        val label: String
    )

    data class StateSyncResponse(
        val status: String = "ok",
        val apps: Int,
        val policies: Int,
        val features: Int = 0
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
        val lockDelayStartedAtMillis: Long? = null,
        val source: String? = null
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
                lockDelayStartedAtMillis = lockDelayStartedAtMillis,
                source = source ?: "DASHBOARD"
            )
        }
    }

    private fun PolicyRuleEntity.toRemotePolicy(): RemotePolicy {
        return RemotePolicy(
            id = id,
            moduleType = moduleType.name,
            targetType = targetType.name,
            packageName = packageName,
            featureId = featureId,
            dailyLimitMinutes = dailyLimitMinutes,
            blockMode = blockMode.name,
            enabled = enabled,
            scheduleJson = scheduleJson,
            metadataJson = metadataJson,
            lockMode = lockMode.name,
            lockUntilDayEndMillis = lockUntilDayEndMillis,
            lockUntilCustomMillis = lockUntilCustomMillis,
            lockOnBlockActive = lockOnBlockActive,
            lockDelayMinutes = lockDelayMinutes,
            lockDelayStartedAtMillis = lockDelayStartedAtMillis,
            source = source
        )
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
