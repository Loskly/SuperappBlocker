package com.ecosentinel.appblocker.engine

import com.ecosentinel.appblocker.modules.inapp.InAppFeature

enum class BlockModuleType {
    APP_LIMIT,
    FOCUS,
    IN_APP_FEATURE,
    ADULT_CONTENT,
    TASK_GATE,
    FRIEND_PASSWORD
}

enum class TargetType {
    APP,
    CATEGORY,
    CUSTOM_GROUP,
    IN_APP_FEATURE,
    URL_PATTERN
}

enum class BlockAction {
    ALLOW,
    BLOCK,
    REQUIRE_TASK,
    REQUIRE_FRIEND_PASSWORD
}

data class BlockDecision(
    val action: BlockAction,
    val reason: String,
    val moduleType: BlockModuleType
)

data class BlockContext(
    val foregroundPackage: String?,
    val usageMillisToday: Map<String, Long> = emptyMap(),
    val nowMillis: Long = System.currentTimeMillis(),
    val currentBrowserUrl: String? = null,
    val currentInAppFeature: InAppFeature? = null
)

interface BlockModule {
    val type: BlockModuleType
    val enabled: Boolean
    fun evaluate(context: BlockContext): BlockDecision?
}
