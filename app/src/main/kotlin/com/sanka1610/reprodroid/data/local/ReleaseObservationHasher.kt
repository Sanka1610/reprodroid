package com.sanka1610.reprodroid.data.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.put
import org.erdtman.jcs.JsonCanonicalizer
import java.security.MessageDigest

internal data class ReleaseObservationInput(
    val provider: String,
    val instance: String,
    val providerRepositoryId: String,
    val providerReleaseId: String,
    val tagName: String,
    val resolvedCommitSha: String,
    val targetCommitishRaw: String,
    val releaseName: String,
    val releaseUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val isImmutable: Boolean,
    val releaseCreatedAt: String,
    val publishedAt: String?,
    val providerAssetId: String?,
    val assetName: String?,
    val stableAssetUrl: String?,
    val contentType: String?,
    val providerSizeBytes: Long?,
    val providerDigestSha256: String?,
    val selectionReason: String?,
    val manualCandidates: List<ReleaseObservationCandidate> = emptyList(),
)

internal data class ReleaseObservationCandidate(
    val providerAssetId: String,
    val assetName: String,
    val stableAssetUrl: String,
    val contentType: String?,
    val providerSizeBytes: Long,
    val providerDigestSha256: String?,
)

internal data class ReleaseMetadataObservationInput(
    val provider: String,
    val instance: String,
    val providerRepositoryId: String,
    val providerReleaseId: String,
    val tagName: String,
    val resolvedCommitSha: String,
    val targetCommitishRaw: String,
    val releaseName: String,
    val releaseUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val isImmutable: Boolean,
    val releaseCreatedAt: String,
    val publishedAt: String?,
    val candidates: List<ReleaseMetadataObservationCandidate>,
)

internal data class ReleaseMetadataObservationCandidate(
    val providerAssetId: String,
    val assetName: String,
    val stableAssetUrl: String,
    val contentType: String?,
    val providerSizeBytes: Long,
    val providerDigestSha256: String?,
    val providerCreatedAt: String?,
)

internal object ReleaseObservationHasher {
    fun sha256(input: ReleaseObservationInput): String {
        val payload = buildJsonObject {
            put("schemaVersion", 1)
            put("provider", input.provider)
            put("instance", input.instance)
            put("providerRepositoryId", input.providerRepositoryId)
            put("providerReleaseId", input.providerReleaseId)
            put("tagName", input.tagName)
            put("resolvedCommitSha", input.resolvedCommitSha)
            put("targetCommitishRaw", input.targetCommitishRaw)
            put("releaseName", input.releaseName)
            put("releaseUrl", input.releaseUrl)
            put("draft", input.isDraft)
            put("prerelease", input.isPrerelease)
            put("immutable", input.isImmutable)
            put("createdAt", input.releaseCreatedAt)
            put("publishedAt", input.publishedAt)
            if (input.providerAssetId == null) {
                put("selectedAsset", JsonNull)
            } else {
                put("selectedAsset", buildJsonObject {
                    put("providerAssetId", input.providerAssetId)
                    put("name", requireNotNull(input.assetName))
                    put("stableUrl", requireNotNull(input.stableAssetUrl))
                    put("contentType", requireNotNull(input.contentType))
                    put("providerSizeBytes", requireNotNull(input.providerSizeBytes).toString())
                    input.providerDigestSha256?.let { put("providerDigestSha256", it) }
                    put("selectionReason", requireNotNull(input.selectionReason))
                })
            }
            if (input.manualCandidates.isNotEmpty()) {
                put("manualCandidates", buildJsonArray {
                    input.manualCandidates
                        .sortedWith(
                            compareBy<ReleaseObservationCandidate> { it.providerAssetId.length }
                                .thenBy { it.providerAssetId }
                                .thenBy { it.assetName },
                        )
                        .forEach { candidate ->
                            add(buildJsonObject {
                                put("providerAssetId", candidate.providerAssetId)
                                put("name", candidate.assetName)
                                put("stableUrl", candidate.stableAssetUrl)
                                put("contentType", candidate.contentType)
                                put("providerSizeBytes", candidate.providerSizeBytes.toString())
                                candidate.providerDigestSha256?.let { put("providerDigestSha256", it) }
                            })
                        }
                })
            }
        }
        return canonicalSha256(payload.toString())
    }


    fun metadataSha256(input: ReleaseMetadataObservationInput): String {
        val payload = buildJsonObject {
            put("schemaVersion", 2)
            put("kind", "PROVIDER_METADATA")
            put("provider", input.provider)
            put("instance", input.instance)
            put("providerRepositoryId", input.providerRepositoryId)
            put("providerReleaseId", input.providerReleaseId)
            put("tagName", input.tagName)
            put("resolvedCommitSha", input.resolvedCommitSha)
            put("targetCommitishRaw", input.targetCommitishRaw)
            put("releaseName", input.releaseName)
            put("releaseUrl", input.releaseUrl)
            put("draft", input.isDraft)
            put("prerelease", input.isPrerelease)
            put("immutable", input.isImmutable)
            put("createdAt", input.releaseCreatedAt)
            put("publishedAt", input.publishedAt)
            put("candidates", buildJsonArray {
                input.candidates
                    .sortedWith(
                        compareBy<ReleaseMetadataObservationCandidate> { it.providerAssetId.length }
                            .thenBy { it.providerAssetId },
                    )
                    .forEach { candidate ->
                        add(buildJsonObject {
                            put("providerAssetId", candidate.providerAssetId)
                            put("name", candidate.assetName)
                            put("stableUrl", candidate.stableAssetUrl)
                            put("contentType", candidate.contentType)
                            put("providerSizeBytes", candidate.providerSizeBytes.toString())
                            put("providerDigestSha256", candidate.providerDigestSha256)
                            put("providerCreatedAt", candidate.providerCreatedAt)
                        })
                    }
            })
        }
        return canonicalSha256(payload.toString())
    }

    fun downloadedContentSha256(
        metadataObservationSha256: String,
        providerAssetId: String,
        computedRawSha256: String,
    ): String {
        val payload = buildJsonObject {
            put("schemaVersion", 2)
            put("kind", "DOWNLOADED_CONTENT")
            put("metadataObservationSha256", metadataObservationSha256)
            put("providerAssetId", providerAssetId)
            put("computedRawSha256", computedRawSha256)
        }
        return canonicalSha256(payload.toString())
    }

    private fun canonicalSha256(json: String): String = MessageDigest.getInstance("SHA-256")
        .digest(JsonCanonicalizer(json).encodedUTF8)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
