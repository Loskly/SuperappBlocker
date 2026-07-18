package com.ecosentinel.appblocker.engine

import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import java.util.concurrent.TimeUnit

data class RuleLockState(
    val locked: Boolean,
    val reason: RuleLockReason,
    val remainingMillis: Long = 0L
) {
    companion object {
        val UNLOCKED = RuleLockState(
            locked = false,
            reason = RuleLockReason.NONE
        )
    }
}

object RuleLockPolicy {

    fun evaluate(
        rule: PolicyRuleEntity,
        nowMillis: Long,
        isBlockActive: Boolean,
        actor: RuleMutationActor = RuleMutationActor.LOCAL_USER
    ): RuleLockState {
        if (actor == RuleMutationActor.REMOTE_OVERRIDE || rule.lockMode == RuleLockMode.NORMAL) {
            return RuleLockState.UNLOCKED
        }

        val timeRemaining = listOfNotNull(
            rule.lockUntilDayEndMillis?.let { it - nowMillis },
            rule.lockUntilCustomMillis?.let { it - nowMillis }
        )
            .filter { it > 0L }
            .maxOrNull()

        if (timeRemaining != null) {
            return RuleLockState(
                locked = true,
                reason = RuleLockReason.UNTIL_TIME,
                remainingMillis = timeRemaining
            )
        }

        if (rule.lockOnBlockActive && isBlockActive) {
            return RuleLockState(
                locked = true,
                reason = RuleLockReason.BLOCK_ACTIVE
            )
        }

        val delayMinutes = rule.lockDelayMinutes?.takeIf { it > 0 }
        if (delayMinutes != null) {
            val startedAt = rule.lockDelayStartedAtMillis?.takeIf { it > 0L }
            if (startedAt == null) {
                return RuleLockState(
                    locked = true,
                    reason = RuleLockReason.DELAY_NOT_STARTED,
                    remainingMillis = TimeUnit.MINUTES.toMillis(delayMinutes.toLong())
                )
            }

            val remaining = startedAt + TimeUnit.MINUTES.toMillis(delayMinutes.toLong()) - nowMillis
            if (remaining > 0L) {
                return RuleLockState(
                    locked = true,
                    reason = RuleLockReason.DELAY_WAITING,
                    remainingMillis = remaining
                )
            }
        }

        val hasAnyCondition = rule.lockUntilDayEndMillis != null ||
            rule.lockUntilCustomMillis != null ||
            rule.lockOnBlockActive ||
            delayMinutes != null

        return if (hasAnyCondition) {
            RuleLockState.UNLOCKED
        } else {
            RuleLockState(
                locked = true,
                reason = RuleLockReason.STRICT_WITHOUT_CONDITION
            )
        }
    }

    fun shouldStartDelay(rule: PolicyRuleEntity): Boolean {
        return rule.lockMode == RuleLockMode.STRICT &&
            rule.lockDelayMinutes?.let { it > 0 } == true &&
            rule.lockDelayStartedAtMillis?.takeIf { it > 0L } == null
    }
}
