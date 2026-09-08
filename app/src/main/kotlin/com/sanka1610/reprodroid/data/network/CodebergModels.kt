package com.sanka1610.reprodroid.data.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class CodebergRepositoryIdentity(
    override val repository: CodebergRepository,
    override val providerRepositoryId: String,
    override val displayName: String,
    override val defaultBranch: String,
) : ProviderRepositoryIdentity {
    override val provider: String = "CODEBERG"
    override val instance: String = "codeberg.org"
}

@Serializable
data class CodebergRelease(
    @Serializable(with = CanonicalProviderIdSerializer::class)
    override val id: String,
    @SerialName("tag_name") override val tagName: String,
    @SerialName("target_commitish") override val targetCommitish: String = "",
    override val name: String? = null,
    @SerialName("html_url") override val htmlUrl: String,
    override val draft: Boolean = false,
    override val prerelease: Boolean = false,
    override val immutable: Boolean = false,
    @SerialName("created_at") override val createdAt: String,
    @SerialName("published_at") override val publishedAt: String? = null,
    override val assets: List<CodebergReleaseAsset> = emptyList(),
) : ProviderRelease

@Serializable
data class CodebergReleaseAsset(
    @Serializable(with = CanonicalProviderIdSerializer::class)
    override val id: String,
    override val name: String,
    override val size: Long,
    @SerialName("created_at") override val providerCreatedAt: String? = null,
    @SerialName("browser_download_url") override val browserDownloadUrl: String,
    @SerialName("content_type") override val contentType: String? = null,
    override val digest: String? = null,
    @SerialName("type") override val providerAssetType: String? = null,
) : ProviderReleaseAsset

data class ResolvedCodebergRelease(
    override val repository: CodebergRepository,
    override val release: CodebergRelease,
    override val resolvedCommitSha: String,
    override val responseEtag: String?,
    override val candidates: List<ProviderReleaseAssetCandidate>,
    override val selectedAsset: ProviderSelectedReleaseAsset?,
) : ResolvedProviderRelease

@Serializable
internal data class CodebergApiRepositoryIdentity(
    @Serializable(with = CanonicalProviderIdSerializer::class)
    val id: String,
)

class CodebergProviderException(
    val statusCode: Int?,
    val code: String,
    override val message: String,
) : RuntimeException(message)
