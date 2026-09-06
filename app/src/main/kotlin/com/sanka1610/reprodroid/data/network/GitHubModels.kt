package com.sanka1610.reprodroid.data.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

data class GitHubRepository(val owner: String, val name: String) {
    val canonicalUrl: String = "https://github.com/${owner.lowercase()}/${name.lowercase()}"
}

@OptIn(ExperimentalSerializationApi::class)
object CanonicalProviderIdSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CanonicalProviderId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive
            ?: throw IllegalArgumentException("Provider ID must be a JSON integer.")
        require(!primitive.isString) { "Provider ID must not be a quoted string." }
        return canonicalProviderId(primitive.content)
    }

    override fun serialize(encoder: Encoder, value: String) {
        val canonical = canonicalProviderId(value)
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalArgumentException("Provider ID requires a JSON encoder.")
        jsonEncoder.encodeJsonElement(JsonUnquotedLiteral(canonical))
    }
}

fun canonicalProviderId(value: String): String {
    require(CANONICAL_PROVIDER_ID.matches(value)) { "Provider ID must be a positive canonical decimal integer." }
    return value
}

private val CANONICAL_PROVIDER_ID = Regex("[1-9][0-9]{0,39}")

@Serializable
data class GitHubRelease(
    @Serializable(with = CanonicalProviderIdSerializer::class)
    val id: String,
    @SerialName("tag_name") val tagName: String,
    @SerialName("target_commitish") val targetCommitish: String,
    val name: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val immutable: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubReleaseAsset>,
)

@Serializable
data class GitHubReleaseAsset(
    @Serializable(with = CanonicalProviderIdSerializer::class)
    val id: String,
    val name: String,
    val state: String,
    @SerialName("content_type") val contentType: String,
    val size: Long,
    val digest: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

@Serializable
internal data class GitHubGitObject(val type: String, val sha: String, val url: String)

@Serializable
internal data class GitHubRefResponse(val ref: String, val `object`: GitHubGitObject)

@Serializable
internal data class GitHubTagResponse(val sha: String, val `object`: GitHubGitObject)

@Serializable
internal data class GitHubApiError(val message: String? = null)

@Serializable
internal data class GitHubApiRepositoryIdentity(
    @Serializable(with = CanonicalProviderIdSerializer::class)
    val id: String,
)

data class ResolvedGitHubRelease(
    val repository: GitHubRepository,
    val release: GitHubRelease,
    val resolvedCommitSha: String,
    val responseEtag: String?,
    val candidates: List<ReleaseAssetCandidate>,
    val selectedAsset: SelectedReleaseAsset?,
)

data class ReleaseAssetCandidate(
    val asset: GitHubReleaseAsset,
    val providerSha256: String?,
)

data class SelectedReleaseAsset(
    val asset: GitHubReleaseAsset,
    val reason: String,
    val providerSha256: String?,
)
