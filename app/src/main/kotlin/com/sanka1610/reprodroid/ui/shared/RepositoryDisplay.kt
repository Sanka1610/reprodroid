package com.sanka1610.reprodroid.ui.shared

import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import java.net.URI

internal fun RegisteredAppRecord.displayAuthor(): String =
    app.authorDisplayOverride?.takeIf(String::isNotBlank)
        ?: repositoryOwner(repositoryDisplayUrl(app.repositoryUrl, app.canonicalRepositoryUrl, latestRelease?.snapshot?.releaseUrl))

internal fun repositoryDisplayUrl(storedUrl: String, canonicalUrl: String, releaseUrl: String?): String {
    val canonical = runCatching { URI(canonicalUrl) }.getOrNull() ?: return canonicalUrl
    val expectedPath = canonical.path.trimEnd('/').removeSuffix(".git")
    fun repositoryUrl(value: String?): String? {
        val uri = value?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        val parts = uri.path?.trim('/')?.split('/') ?: return null
        if (uri.scheme != "https" || !uri.host.equals(canonical.host, ignoreCase = true) ||
            uri.userInfo != null || uri.port != -1 || parts.size < 2) return null
        val path = "/${parts[0]}/${parts[1].removeSuffix(".git")}"
        return "https://${canonical.host}$path".takeIf { path.equals(expectedPath, ignoreCase = true) }
    }
    val stored = repositoryUrl(storedUrl)
    val release = repositoryUrl(releaseUrl)
    return listOfNotNull(stored, release).firstOrNull { URI(it).path.any(Char::isUpperCase) }
        ?: stored ?: release ?: canonicalUrl
}
