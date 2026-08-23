package com.sanka1610.reprodroid.data.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class GitHubRepository(val owner: String, val name: String) {
    val canonicalUrl: String = "https://github.com/${owner.lowercase()}/${name.lowercase()}"
}

@Serializable
data class GitHubRelease(
    val id: Long,
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
    val id: Long,
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

data class ResolvedGitHubRelease(
    val repository: GitHubRepository,
    val release: GitHubRelease,
    val resolvedCommitSha: String,
    val responseEtag: String?,
    val selectedAsset: SelectedReleaseAsset,
)

data class SelectedReleaseAsset(
    val asset: GitHubReleaseAsset,
    val reason: String,
    val providerSha256: String?,
)
