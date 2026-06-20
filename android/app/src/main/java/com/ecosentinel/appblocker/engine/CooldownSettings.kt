package com.ecosentinel.appblocker.engine

import org.json.JSONObject

data class CooldownSettings(
    val usageMinutes: Int,
    val windowMinutes: Int,
    val blockMinutes: Int
) {
    fun isValid(): Boolean {
        return usageMinutes > 0 && windowMinutes > 0 && blockMinutes > 0
    }

    fun toJson(): String {
        return JSONObject()
            .put("usageMinutes", usageMinutes)
            .put("windowMinutes", windowMinutes)
            .put("blockMinutes", blockMinutes)
            .toString()
    }

    fun formatSummary(): String {
        return "$usageMinutes мин / $windowMinutes мин → $blockMinutes мин"
    }

    companion object {
        fun fromJson(json: String?): CooldownSettings? {
            if (json.isNullOrBlank()) {
                return null
            }
            return try {
                val objectJson = JSONObject(json)
                CooldownSettings(
                    usageMinutes = objectJson.getInt("usageMinutes"),
                    windowMinutes = objectJson.getInt("windowMinutes"),
                    blockMinutes = objectJson.getInt("blockMinutes")
                )
            } catch (_: Exception) {
                null
            }
        }

        val DEFAULT = CooldownSettings(
            usageMinutes = 10,
            windowMinutes = 60,
            blockMinutes = 15
        )
    }
}
