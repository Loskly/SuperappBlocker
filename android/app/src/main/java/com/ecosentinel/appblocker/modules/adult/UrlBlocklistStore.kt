package com.ecosentinel.appblocker.modules.adult

import android.content.Context
import android.net.Uri
import com.ecosentinel.appblocker.R

class UrlBlocklistStore(context: Context) {

    private val appContext = context.applicationContext
    private var builtInHosts: Set<String> = emptySet()

    @Synchronized
    fun ensureLoaded() {
        if (builtInHosts.isNotEmpty()) {
            return
        }
        builtInHosts = appContext.resources.openRawResource(R.raw.adult_hosts)
            .bufferedReader()
            .useLines { lines ->
                lines.mapNotNull { line ->
                    val trimmed = line.trim().lowercase()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        null
                    } else {
                        AdultFilterSettings.normalizeDomain(trimmed)
                    }
                }.toSet()
            }
    }

    fun isBlocked(context: Context, url: String): Boolean {
        ensureLoaded()
        val host = extractHost(url) ?: return false
        val domains = builtInHosts + AdultFilterSettings.getCustomDomains(context)
        return domains.any { domain -> hostMatches(host, domain) }
    }

    fun blockedHost(context: Context, url: String): String? {
        ensureLoaded()
        val host = extractHost(url) ?: return null
        val domains = builtInHosts + AdultFilterSettings.getCustomDomains(context)
        return domains.firstOrNull { domain -> hostMatches(host, domain) }
    }

    fun builtInCount(): Int {
        ensureLoaded()
        return builtInHosts.size
    }

    companion object {
        fun extractHost(url: String): String? {
            val raw = url.trim()
            if (raw.isEmpty()) {
                return null
            }
            val withScheme = when {
                raw.contains("://") -> raw
                raw.contains('.') && !raw.contains(' ') -> "https://$raw"
                else -> return null
            }
            return try {
                Uri.parse(withScheme).host
                    ?.lowercase()
                    ?.removePrefix("www.")
                    ?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }

        fun hostMatches(host: String, blockedDomain: String): Boolean {
            val domain = blockedDomain.lowercase().removePrefix("www.")
            return host == domain || host.endsWith(".$domain")
        }
    }
}
