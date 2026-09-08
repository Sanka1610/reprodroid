package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.AssetSelectionReason
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SavedAssetSelectionCondition(
    val schemaVersion: Int = SCHEMA_VERSION,
    val abi: String? = null,
    val variant: String? = null,
    val exactFilename: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

object SavedAssetSelection {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(condition: SavedAssetSelectionCondition): String {
        validate(condition)
        return json.encodeToString(condition)
    }

    fun decode(value: String): SavedAssetSelectionCondition =
        json.decodeFromString<SavedAssetSelectionCondition>(value).also(::validate)

    fun select(
        candidates: List<out ProviderAssetCandidate>,
        encodedCondition: String?,
    ): ProviderSelectedAsset? {
        val selected = selectUnique(candidates, encodedCondition) { it.asset.name } ?: return null
        val reason = if (candidates.size == 1) {
            AssetSelectionReason.SINGLE_APK.name
        } else {
            AssetSelectionReason.SAVED_SELECTION_CONDITION.name
        }
        return ProviderSelectedReleaseAsset(
            asset = selected.asset,
            reason = reason,
            providerSha256 = selected.providerSha256,
        )
    }

    fun selectFilename(candidates: List<String>, encodedCondition: String?): String? =
        selectUnique(candidates, encodedCondition) { it }

    private fun <T> selectUnique(
        candidates: List<T>,
        encodedCondition: String?,
        filename: (T) -> String,
    ): T? {
        if (candidates.size == 1) return candidates.single()
        if (candidates.isEmpty() || encodedCondition == null) return null
        val condition = decode(encodedCondition)
        val abi = condition.abi?.let(PreferredAbi::valueOf)
        val variant = condition.variant?.let(ReleaseVariantPreference::valueOf)
        return candidates.filter { candidate ->
            val name = filename(candidate)
            (condition.exactFilename == null || name == condition.exactFilename) &&
                (abi == null || abi.matches(name)) &&
                (variant == null || variant.matches(name))
        }.singleOrNull()
    }

    private fun validate(condition: SavedAssetSelectionCondition) {
        require(condition.schemaVersion == SavedAssetSelectionCondition.SCHEMA_VERSION) {
            "Unsupported saved APK selection schema version."
        }
        require(condition.abi != null || condition.variant != null || condition.exactFilename != null) {
            "A saved APK selection requires at least one condition."
        }
        condition.abi?.let(PreferredAbi::valueOf)
        condition.variant?.let(ReleaseVariantPreference::valueOf)
        condition.exactFilename?.let { filename ->
            require(filename.isNotBlank() && filename.length <= 1024) { "The exact APK filename is invalid." }
            require('/' !in filename && '\\' !in filename && filename.none(Char::isISOControl)) {
                "The exact APK filename must not contain a path or control character."
            }
        }
    }

    private fun PreferredAbi.matches(filename: String): Boolean = when (this) {
        PreferredAbi.ARM64_V8A -> token("arm64[-_]v8a").containsMatchIn(filename)
        PreferredAbi.ARMEABI_V7A -> token("(?:armeabi|arm)[-_]v7a").containsMatchIn(filename)
        PreferredAbi.X86_64 -> token("x86[-_]64").containsMatchIn(filename)
        PreferredAbi.UNIVERSAL -> token("universal").containsMatchIn(filename)
    }

    private fun ReleaseVariantPreference.matches(filename: String): Boolean = when (this) {
        ReleaseVariantPreference.RELEASE ->
            !token("preview").containsMatchIn(filename) && !token("debug").containsMatchIn(filename)
        ReleaseVariantPreference.PREVIEW -> token("preview").containsMatchIn(filename)
        ReleaseVariantPreference.DEBUG -> token("debug").containsMatchIn(filename)
    }

    private fun token(value: String) = Regex("(^|[^a-z0-9])$value([^a-z0-9]|$)", RegexOption.IGNORE_CASE)
}
