package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ProviderRepresentationEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import java.time.Instant

/**
 * Provider-neutral values used by the registration, release and scheduled-check paths.
 * Provider adapters remain responsible for parsing and validating their own wire format.
 */
interface ProviderRepositoryLocator {
    val owner: String
    val name: String
    val canonicalUrl: String
    val host: String
}

interface ProviderRepositoryIdentity {
    val repository: ProviderRepositoryLocator
    val provider: String
    val instance: String
    val providerRepositoryId: String
    val displayName: String
    val defaultBranch: String
}

interface ProviderReleaseAsset {
    val id: String
    val name: String
    val contentType: String?
    val size: Long
    val digest: String?
    val browserDownloadUrl: String
    val providerCreatedAt: String?
    /** Provider wire type; null means the adapter did not expose one. */
    val providerAssetType: String?
}

interface ProviderRelease {
    val id: String
    val tagName: String
    val targetCommitish: String
    val name: String?
    val htmlUrl: String
    val draft: Boolean
    val prerelease: Boolean
    val immutable: Boolean
    val createdAt: String
    val publishedAt: String?
    val assets: List<out ProviderReleaseAsset>
}

interface ProviderAssetCandidate {
    val asset: ProviderReleaseAsset
    val providerSha256: String?
}

data class ProviderReleaseAssetCandidate(
    override val asset: ProviderReleaseAsset,
    override val providerSha256: String?,
) : ProviderAssetCandidate

data class ProviderSelectedReleaseAsset(
    override val asset: ProviderReleaseAsset,
    override val reason: String,
    override val providerSha256: String?,
) : ProviderSelectedAsset

interface ProviderSelectedAsset : ProviderAssetCandidate {
    val reason: String
}

interface ResolvedProviderRelease {
    val repository: ProviderRepositoryLocator
    val release: ProviderRelease
    val resolvedCommitSha: String
    val responseEtag: String?
    val candidates: List<out ProviderAssetCandidate>
    val selectedAsset: ProviderSelectedAsset?
}

interface ProviderReleaseClient {
    val providerName: String
    val providerInstance: String

    suspend fun resolveLatestRelease(
        repositoryUrl: String,
        previousEtag: String?,
        preferredAbi: PreferredAbi,
        preferredVariant: ReleaseVariantPreference,
    ): ResolvedProviderRelease
}

interface ProviderRepositoryDiscoveryClient {
    val providerName: String
    val providerInstance: String

    suspend fun preview(repositoryUrl: String): RepositoryRegistrationPreview
}

sealed interface ProviderMetadataResult {
    val requestCount: Int
    val receivedBytes: Long
    val representations: List<ProviderRepresentationEntity>

    data class Release(
        val resolved: ResolvedProviderRelease,
        val representationNotModified: Boolean,
        override val requestCount: Int,
        override val receivedBytes: Long,
        override val representations: List<ProviderRepresentationEntity>,
    ) : ProviderMetadataResult

    data class NoPublishedRelease(
        override val requestCount: Int,
        override val receivedBytes: Long,
        override val representations: List<ProviderRepresentationEntity>,
    ) : ProviderMetadataResult
}

interface ProviderReleaseMetadataClient {
    val providerName: String
    val providerInstance: String

    suspend fun check(
        repositoryUrl: String,
        providerRepositoryId: String,
        channel: ReleaseCheckChannel,
        cachedRepresentations: List<ProviderRepresentationEntity>,
        now: Instant,
    ): ProviderMetadataResult
}
