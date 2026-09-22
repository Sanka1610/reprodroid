package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.AssetSelectionReason
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import java.net.URI

class ReleaseAssetSelectionException(val code: String, override val message: String) : RuntimeException(message)

object ReleaseAssetSelector {
    const val MAX_ASSET_SIZE_BYTES: Long = 512L * 1024L * 1024L

    /**
     * Selects provider-neutral APK candidates. The provider adapter supplies its own
     * stable host allow-list; the downloaded bytes are still inspected as an APK.
     */
    fun providerCandidates(
        assets: List<out ProviderReleaseAsset>,
        stableHosts: Set<String>,
        isSupportedAttachment: (ProviderReleaseAsset) -> Boolean = { true },
        stableUrlPredicate: ((ProviderReleaseAsset) -> Boolean)? = null,
    ): List<ProviderReleaseAssetCandidate> {
        val uploadedApks = assets.filter { it.name.lowercase().endsWith(".apk") }
        if (uploadedApks.isEmpty()) {
            throw ReleaseAssetSelectionException("NO_APK_ASSET", "The latest release has no supported APK asset.")
        }
        val invalidCandidate = uploadedApks.firstOrNull {
            !isSupportedAttachment(it) || !isProviderApkCandidate(it, stableHosts, stableUrlPredicate)
        }
        if (invalidCandidate != null) {
            throw ReleaseAssetSelectionException(
                "INVALID_APK_ASSET_METADATA",
                "APK asset metadata violates the MIME, size, or stable URL policy: ${invalidCandidate.name}",
            )
        }
        return uploadedApks.map { asset ->
            ProviderReleaseAssetCandidate(asset = asset, providerSha256 = parseProviderSha256(asset.digest))
        }
    }

    fun selectProviderValidatedCandidates(
        candidates: List<out ProviderAssetCandidate>,
        preferredAbi: PreferredAbi = PreferredAbi.ARM64_V8A,
        preferredVariant: ReleaseVariantPreference = ReleaseVariantPreference.RELEASE,
    ): ProviderSelectedReleaseAsset {
        require(candidates.isNotEmpty()) { "Validated APK candidates must not be empty." }
        val selected = when (candidates.size) {
            1 -> candidates.single() to AssetSelectionReason.SINGLE_APK.name
            else -> {
                val abiCandidates = candidates.filter { preferredAbi.filenameToken().containsMatchIn(it.asset.name) }
                val variantCandidates = abiCandidates.filter { preferredVariant.matchesFilename(it.asset.name) }
                if (variantCandidates.size != 1) {
                    throw ReleaseAssetSelectionException(
                        "AMBIGUOUS_APK_ASSETS",
                        "Multiple APK assets do not resolve to exactly one preferred ABI and variant.",
                    )
                }
                val reason = if (
                    abiCandidates.size == 1 &&
                    !EXPLICIT_VARIANT_TOKEN.containsMatchIn(abiCandidates.single().asset.name)
                ) {
                    AssetSelectionReason.PREFERRED_ABI_FILENAME.name
                } else {
                    AssetSelectionReason.PREFERRED_ABI_AND_VARIANT_FILENAME.name
                }
                variantCandidates.single() to reason
            }
        }
        return ProviderSelectedReleaseAsset(
            asset = selected.first.asset,
            reason = selected.second,
            providerSha256 = selected.first.providerSha256,
        )
    }

    fun candidates(assets: List<GitHubReleaseAsset>): List<ReleaseAssetCandidate> {
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
        return uploadedApks.map { asset ->
            ReleaseAssetCandidate(asset = asset, providerSha256 = parseProviderSha256(asset.digest))
        }
    }

    fun select(
        assets: List<GitHubReleaseAsset>,
        preferredAbi: PreferredAbi = PreferredAbi.ARM64_V8A,
        preferredVariant: ReleaseVariantPreference = ReleaseVariantPreference.RELEASE,
    ): SelectedReleaseAsset = selectValidatedCandidates(candidates(assets), preferredAbi, preferredVariant)

    fun selectValidatedCandidates(
        candidates: List<ReleaseAssetCandidate>,
        preferredAbi: PreferredAbi = PreferredAbi.ARM64_V8A,
        preferredVariant: ReleaseVariantPreference = ReleaseVariantPreference.RELEASE,
    ): SelectedReleaseAsset {
        require(candidates.isNotEmpty()) { "Validated APK candidates must not be empty." }
        val selected = when (candidates.size) {
            1 -> candidates.single() to AssetSelectionReason.SINGLE_APK.name
            else -> {
                val abiCandidates = candidates.filter { preferredAbi.filenameToken().containsMatchIn(it.asset.name) }
                val variantCandidates = abiCandidates.filter { preferredVariant.matchesFilename(it.asset.name) }
                if (variantCandidates.size != 1) {
                    throw ReleaseAssetSelectionException(
                        "AMBIGUOUS_APK_ASSETS",
                        "Multiple APK assets do not resolve to exactly one preferred ABI and variant.",
                    )
                }
                val reason = if (
                    abiCandidates.size == 1 &&
                    !EXPLICIT_VARIANT_TOKEN.containsMatchIn(abiCandidates.single().asset.name)
                ) {
                    AssetSelectionReason.PREFERRED_ABI_FILENAME.name
                } else {
                    AssetSelectionReason.PREFERRED_ABI_AND_VARIANT_FILENAME.name
                }
                variantCandidates.single() to reason
            }
        }
        return SelectedReleaseAsset(
            asset = selected.first.asset,
            reason = selected.second,
            providerSha256 = selected.first.providerSha256,
        )
    }

    /** Suggests a filename candidate only; downloading still requires the user's action. */
    fun suggestForDevice(
        assets: List<Pair<String, String>>,
        supportedAbis: List<String>,
        variant: ReleaseVariantPreference,
    ): String? {
        val matchingVariant = assets.filter { variant.matchesFilename(it.second) }
        if (matchingVariant.isEmpty()) return null
        val knownAbis = supportedAbis.map(String::lowercase).filter { it in DEVICE_ABI_TOKENS }.distinct()
        if (knownAbis.isEmpty()) {
            return assets.singleOrNull()?.takeIf { candidate ->
                candidate in matchingVariant && deviceAbis(candidate.second).isEmpty()
            }?.first
        }
        for (abi in knownAbis) {
            val compatible = matchingVariant.filter { abi in deviceAbis(it.second) }
            if (compatible.isNotEmpty()) return compatible.singleOrNull()?.first
        }
        val universal = matchingVariant.filter { token("universal").containsMatchIn(it.second) }
        if (universal.isNotEmpty()) return universal.singleOrNull()?.first
        return matchingVariant.filter { deviceAbis(it.second).isEmpty() }.singleOrNull()?.first
    }

    private fun deviceAbis(filename: String): Set<String> = DEVICE_ABI_TOKENS
        .filterValues { it.containsMatchIn(filename) }.keys

    private val DEVICE_ABI_TOKENS = mapOf(
        "arm64-v8a" to token("arm64(?:[-_]v8a)?|aarch64"),
        "armeabi-v7a" to token("(?:armeabi|arm)[-_]v7a|armv7a?"),
        "armeabi" to token("armeabi(?![-_]v7a)"),
        "x86_64" to token("x86[-_]64|amd64"),
        "x86" to token("x86(?![-_]64)|i686"),
        "riscv64" to token("riscv64"),
    )

    private fun isApkCandidate(asset: GitHubReleaseAsset): Boolean =
        asset.state == "uploaded" &&
            asset.name.lowercase().endsWith(".apk") &&
            asset.contentType.equals(APK_CONTENT_TYPE, ignoreCase = true) &&
            asset.size in 1..MAX_ASSET_SIZE_BYTES &&
            isStableGitHubDownloadUrl(asset.browserDownloadUrl)

    private fun isProviderApkCandidate(
        asset: ProviderReleaseAsset,
        stableHosts: Set<String>,
        stableUrlPredicate: ((ProviderReleaseAsset) -> Boolean)?,
    ): Boolean {
        val uri = runCatching { URI(asset.browserDownloadUrl) }.getOrNull() ?: return false
        val normalizedContentType = asset.contentType?.substringBefore(';')?.trim()?.lowercase()
        val contentTypeAllowed = normalizedContentType == null ||
            normalizedContentType == APK_CONTENT_TYPE ||
            normalizedContentType in GENERIC_APK_CONTENT_TYPES
        return asset.name.lowercase().endsWith(".apk") &&
            contentTypeAllowed &&
            asset.size in 1..MAX_ASSET_SIZE_BYTES &&
            uri.scheme?.lowercase() == "https" &&
            uri.host?.lowercase() in stableHosts.map(String::lowercase).toSet() &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.query == null &&
            uri.fragment == null &&
            (stableUrlPredicate?.invoke(asset) ?: uri.path.contains("/releases/download/"))
    }

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
    private val GENERIC_APK_CONTENT_TYPES = setOf("application/octet-stream")
    private val PROVIDER_DIGEST = Regex("sha256:([0-9a-f]{64})")
    private fun PreferredAbi.filenameToken(): Regex = when (this) {
        PreferredAbi.ARM64_V8A -> token("arm64[-_]v8a")
        PreferredAbi.ARMEABI_V7A -> token("(?:armeabi|arm)[-_]v7a")
        PreferredAbi.X86_64 -> token("x86[-_]64")
        PreferredAbi.UNIVERSAL -> token("universal")
    }

    private fun ReleaseVariantPreference.matchesFilename(filename: String): Boolean = when (this) {
        ReleaseVariantPreference.RELEASE ->
            !PREVIEW_TOKEN.containsMatchIn(filename) && !DEBUG_TOKEN.containsMatchIn(filename)
        ReleaseVariantPreference.PREVIEW -> PREVIEW_TOKEN.containsMatchIn(filename)
        ReleaseVariantPreference.DEBUG -> DEBUG_TOKEN.containsMatchIn(filename)
    }

    private fun token(value: String) = Regex("(^|[^a-z0-9])(?:$value)([^a-z0-9]|$)", RegexOption.IGNORE_CASE)

    private val PREVIEW_TOKEN = token("preview")
    private val DEBUG_TOKEN = token("debug")
    private val EXPLICIT_VARIANT_TOKEN = token("(?:release|preview|debug)")
}
