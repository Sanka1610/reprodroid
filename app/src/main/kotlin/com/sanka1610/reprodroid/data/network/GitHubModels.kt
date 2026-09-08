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

data class GitHubRepository(override val owner: String, override val name: String) : ProviderRepositoryLocator {
    override val host: String = "github.com"
    override val canonicalUrl: String = "https://github.com/${owner.lowercase()}/${name.lowercase()}"
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
    override val id: String,
    @SerialName("tag_name") override val tagName: String,
    @SerialName("target_commitish") override val targetCommitish: String,
    override val name: String? = null,
    @SerialName("html_url") override val htmlUrl: String,
    override val draft: Boolean,
    override val prerelease: Boolean,
    override val immutable: Boolean = false,
    @SerialName("created_at") override val createdAt: String,
    @SerialName("published_at") override val publishedAt: String? = null,
    override val assets: List<GitHubReleaseAsset>,
) : ProviderRelease

@Serializable
data class GitHubReleaseAsset(
    @Serializable(with = CanonicalProviderIdSerializer::class)
    override val id: String,
    override val name: String,
    val state: String,
    @SerialName("content_type") override val contentType: String? = null,
    override val size: Long,
    override val digest: String? = null,
    @SerialName("browser_download_url") override val browserDownloadUrl: String,
    @SerialName("created_at") override val providerCreatedAt: String? = null,
    @SerialName("type") override val providerAssetType: String? = null,
) : ProviderReleaseAsset

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
    override val repository: GitHubRepository,
    override val release: GitHubRelease,
    override val resolvedCommitSha: String,
    override val responseEtag: String?,
    override val candidates: List<ReleaseAssetCandidate>,
    override val selectedAsset: SelectedReleaseAsset?,
) : ResolvedProviderRelease

data class ReleaseAssetCandidate(
    override val asset: GitHubReleaseAsset,
    override val providerSha256: String?,
) : ProviderAssetCandidate

data class SelectedReleaseAsset(
    override val asset: GitHubReleaseAsset,
    override val reason: String,
    override val providerSha256: String?,
) : ProviderSelectedAsset
