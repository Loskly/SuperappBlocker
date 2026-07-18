package com.ecosentinel.appblocker.engine

enum class RuleLockMode {
    NORMAL,
    STRICT
}

enum class RuleMutationActor {
    LOCAL_USER,
    REMOTE_OVERRIDE
}

enum class RuleLockReason {
    NONE,
    UNTIL_TIME,
    BLOCK_ACTIVE,
    DELAY_NOT_STARTED,
    DELAY_WAITING,
    STRICT_WITHOUT_CONDITION
}

data class RuleLockConfig(
    val mode: RuleLockMode = RuleLockMode.NORMAL,
    val untilDayEndMillis: Long? = null,
    val untilCustomMillis: Long? = null,
    val onBlockActive: Boolean = false,
    val delayMinutes: Int? = null,
    val delayStartedAtMillis: Long? = null
) {
    companion object {
        val NORMAL = RuleLockConfig()
    }
}
