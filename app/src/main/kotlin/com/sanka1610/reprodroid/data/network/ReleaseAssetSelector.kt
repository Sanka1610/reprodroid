package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.AssetSelectionReason
import java.net.URI

class ReleaseAssetSelectionException(val code: String, override val message: String) : RuntimeException(message)

object ReleaseAssetSelector {
    const val MAX_ASSET_SIZE_BYTES: Long = 512L * 1024L * 1024L

    fun select(assets: List<GitHubReleaseAsset>): SelectedReleaseAsset {
        val uploadedApks = assets.filter { it.state == "uploaded" && it.name.lowercase().endsWith(".apk") }
        if (uploadedApks.isEmpty()) {
            throw ReleaseAssetSelectionException("NO_APK_ASSET", "The latest release has no supported APK asset.")
        }
        val invalidCandidate = uploadedApks.firstOrNull { !isApkCandidate(it) }
        if (invalidCandidate != null) {
            throw ReleaseAssetSelectionException(
                "INVALID_APK_ASSET_METADATA",
                "APK asset metadata violates the MIME, size, or stable URL policy: ${invalidCandidate.name}",
            )
        }
        val candidates = uploadedApks
        val selected = when (candidates.size) {
            1 -> candidates.single() to AssetSelectionReason.SINGLE_APK.name
            else -> {
                val arm64Candidates = candidates.filter { ARM64_TOKEN.containsMatchIn(it.name) }
                if (arm64Candidates.size != 1) {
                    throw ReleaseAssetSelectionException(
                        "AMBIGUOUS_APK_ASSETS",
                        "The latest release has multiple APK assets but not exactly one arm64-v8a candidate.",
                    )
                }
                arm64Candidates.single() to AssetSelectionReason.ARM64_V8A_FILENAME.name
            }
        }
        return SelectedReleaseAsset(
            asset = selected.first,
            reason = selected.second,
            providerSha256 = parseProviderSha256(selected.first.digest),
        )
    }

    private fun isApkCandidate(asset: GitHubReleaseAsset): Boolean =
        asset.state == "uploaded" &&
            asset.name.lowercase().endsWith(".apk") &&
            asset.contentType.equals(APK_CONTENT_TYPE, ignoreCase = true) &&
            asset.size in 1..MAX_ASSET_SIZE_BYTES &&
            isStableGitHubDownloadUrl(asset.browserDownloadUrl)

    private fun isStableGitHubDownloadUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() == "https" &&
            uri.host?.lowercase() == "github.com" &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path.contains("/releases/download/")
    }

    private fun parseProviderSha256(digest: String?): String? {
        if (digest == null) return null
        val match = PROVIDER_DIGEST.matchEntire(digest.lowercase())
            ?: throw ReleaseAssetSelectionException(
                "UNSUPPORTED_PROVIDER_DIGEST",
                "The selected release asset has a malformed or unsupported digest.",
            )
        return match.groupValues[1]
    }

    private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
    private val PROVIDER_DIGEST = Regex("sha256:([0-9a-f]{64})")
    private val ARM64_TOKEN = Regex("(^|[^a-z0-9])arm64[-_]v8a([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
}
