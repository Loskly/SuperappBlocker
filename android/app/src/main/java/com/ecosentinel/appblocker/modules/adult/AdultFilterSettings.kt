package com.ecosentinel.appblocker.modules.adult

import android.content.Context

object AdultFilterSettings {

    private const val PREFS_NAME = "adult_filter_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CUSTOM_DOMAINS = "custom_domains"

    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getCustomDomains(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_CUSTOM_DOMAINS, emptySet()).orEmpty()
    }

    fun addCustomDomain(context: Context, domain: String) {
        val normalized = normalizeDomain(domain) ?: return
        val updated = getCustomDomains(context).toMutableSet()
        updated.add(normalized)
        prefs(context).edit().putStringSet(KEY_CUSTOM_DOMAINS, updated).apply()
    }

    fun removeCustomDomain(context: Context, domain: String) {
        val normalized = normalizeDomain(domain) ?: return
        val updated = getCustomDomains(context).toMutableSet()
        updated.remove(normalized)
        prefs(context).edit().putStringSet(KEY_CUSTOM_DOMAINS, updated).apply()
    }

    fun normalizeDomain(raw: String): String? {
        val trimmed = raw.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore(':')
            .removePrefix("www.")
        if (trimmed.isEmpty() || !trimmed.contains('.')) {
            return null
        }
        return trimmed
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
