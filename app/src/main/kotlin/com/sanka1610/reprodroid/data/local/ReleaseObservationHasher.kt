package com.sanka1610.reprodroid.data.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.put
import org.erdtman.jcs.JsonCanonicalizer
import java.security.MessageDigest

internal data class ReleaseObservationInput(
    val provider: String,
    val instance: String,
    val providerRepositoryId: String,
    val providerReleaseId: Long,
    val tagName: String,
    val resolvedCommitSha: String,
    val targetCommitishRaw: String,
    val releaseName: String,
    val releaseUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val isImmutable: Boolean,
    val releaseCreatedAt: String,
    val publishedAt: String,
    val providerAssetId: Long?,
    val assetName: String?,
    val stableAssetUrl: String?,
    val contentType: String?,
    val providerSizeBytes: Long?,
    val providerDigestSha256: String?,
    val selectionReason: String?,
)

internal object ReleaseObservationHasher {
    fun sha256(input: ReleaseObservationInput): String {
        val payload = buildJsonObject {
            put("schemaVersion", 1)
            put("provider", input.provider)
            put("instance", input.instance)
            put("providerRepositoryId", input.providerRepositoryId)
            put("providerReleaseId", input.providerReleaseId.toString())
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
                    put("providerAssetId", input.providerAssetId.toString())
                    put("name", requireNotNull(input.assetName))
                    put("stableUrl", requireNotNull(input.stableAssetUrl))
                    put("contentType", requireNotNull(input.contentType))
                    put("providerSizeBytes", requireNotNull(input.providerSizeBytes).toString())
                    input.providerDigestSha256?.let { put("providerDigestSha256", it) }
                    put("selectionReason", requireNotNull(input.selectionReason))
                })
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(JsonCanonicalizer(payload.toString()).encodedUTF8)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
