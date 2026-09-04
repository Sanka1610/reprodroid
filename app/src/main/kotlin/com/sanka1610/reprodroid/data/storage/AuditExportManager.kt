package com.sanka1610.reprodroid.data.storage

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.sanka1610.reprodroid.data.local.AuditExportEntity
import com.sanka1610.reprodroid.data.local.AuditExportState
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.ResourceAvailabilityState
import com.sanka1610.reprodroid.data.network.BuildSandboxMode
import com.sanka1610.reprodroid.data.network.decodeSandboxEvidence
import com.sanka1610.reprodroid.data.repository.storedSandboxManifestValid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.erdtman.jcs.JsonCanonicalizer
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class StagedAuditExport(
    val auditExportId: String,
    val suggestedName: String,
    val state: String,
    val payloadSha256: String,
    val sizeBytes: Long,
    val recordCount: Int,
    val errorCode: String? = null,
)

internal data class CanonicalAuditBundle(val bytes: ByteArray, val payloadSha256: String)

internal object AuditCanonical {
    fun recordSha256(type: String, payload: JsonObject): String = sha256(
        canonicalBytes(buildJsonObject {
            put("type", type)
            put("schemaVersion", 1)
            put("payload", payload)
        }),
    )

    fun bundle(scope: JsonObject, records: List<JsonObject>, generatedAt: String): CanonicalAuditBundle {
        val payload = buildJsonObject {
            put("schemaVersion", 1)
            put("scope", scope)
            put("records", JsonArray(records))
        }
        val payloadSha256 = sha256(canonicalBytes(payload))
        val root = buildJsonObject {
            put("schemaVersion", 1)
            put("generatedAt", generatedAt)
            put("scope", scope)
            put("records", JsonArray(records))
            put("payloadSha256", payloadSha256)
        }
        return CanonicalAuditBundle(canonicalBytes(root), payloadSha256)
    }

    private fun canonicalBytes(value: JsonObject) = JsonCanonicalizer(value.toString()).encodedUTF8
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

class AuditExportManager(
    private val context: Context,
    private val database: ReproDroidDatabase,
    private val storageManager: AndroidStorageManager,
) {
    private val managedAppDao = database.managedAppDao()
    private val jobDao = database.jobDao()
    private val storageDao = database.storageDao()
    private val mutex = Mutex()

    suspend fun stageAll(): StagedAuditExport = stage("ALL", emptySet())

    suspend fun stageApps(registeredAppIds: Set<String>): StagedAuditExport {
        require(registeredAppIds.isNotEmpty() && registeredAppIds.size <= 100) {
            "Select 1 to 100 registered apps for audit export."
        }
        registeredAppIds.forEach { requireCanonicalUuid(it, "registeredAppId") }
        return stage("APP", registeredAppIds)
    }

    suspend fun latest(): StagedAuditExport? = mutex.withLock {
        storageDao.getLatestAuditExport()?.takeIf {
            it.payloadSha256 != null && it.bundleSha256 != null && it.sizeBytes != null && it.recordCount != null
        }?.toResult()
    }

    suspend fun copyTo(auditExportId: String, destination: Uri): StagedAuditExport = mutex.withLock {
        requireCanonicalUuid(auditExportId, "auditExportId")
        val export = storageDao.getAuditExport(auditExportId)
            ?: throw IllegalArgumentException("Audit export was not found.")
        check(export.state == AuditExportState.STAGED.name || export.state == AuditExportState.FAILED.name) {
            "Audit export is not staged for copying."
        }
        val source = verifiedStage(export)
        storageDao.upsertAuditExport(export.copy(state = AuditExportState.COPYING.name, errorCode = null, updatedAt = now()))
        try {
            withContext(Dispatchers.IO) {
                val descriptor = context.contentResolver.openFileDescriptor(destination, "rwt")
                    ?: throw IllegalStateException("The selected destination cannot be opened.")
                descriptor.use { parcel ->
                    FileOutputStream(parcel.fileDescriptor).use { output ->
                        Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input -> input.copyTo(output, COPY_BUFFER_BYTES) }
                        output.flush()
                        output.fd.sync()
                    }
                }
            }
            val completed = export.copy(state = AuditExportState.COMPLETE.name, errorCode = null, updatedAt = now())
            storageDao.upsertAuditExport(completed)
            completed.toResult()
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                storageDao.upsertAuditExport(
                    export.copy(state = AuditExportState.FAILED.name, errorCode = "DESTINATION_WRITE_FAILED", updatedAt = now()),
                )
            }
            throw failure
        }
    }

    suspend fun reconcileInterruptedExports() = mutex.withLock {
        storageDao.getActiveAuditExports().forEach { export ->
            val availability = storageDao.getAvailability(
                AndroidStorageManager.OWNER_ANDROID,
                AndroidStorageManager.OWNER_ANDROID_LOCAL,
                "AUDIT_EXPORT",
                export.auditExportId,
            )
            storageDao.upsertAuditExport(
                export.copy(
                    state = if (availability?.state == ResourceAvailabilityState.PRESENT.name) {
                        AuditExportState.STAGED.name
                    } else {
                        AuditExportState.FAILED.name
                    },
                    errorCode = if (availability?.state == ResourceAvailabilityState.PRESENT.name) null else "STAGING_INTERRUPTED",
                    updatedAt = now(),
                ),
            )
        }
    }

    private suspend fun stage(scopeType: String, ids: Set<String>): StagedAuditExport = mutex.withLock {
        val exportId = UUID.randomUUID().toString()
        val scope = scopeJson(scopeType, ids)
        val records = buildRecords(ids).distinctBy { listOf(it.type, it.stableId, it.sha256) }
        check(records.size <= MAX_RECORDS) { "EXPORT_LIMIT_EXCEEDED" }
        val recordNodes = records.sortedWith(compareBy(AuditRecord::type, AuditRecord::stableId, AuditRecord::order, AuditRecord::sha256))
            .map(AuditRecord::node)
        val canonical = AuditCanonical.bundle(scope, recordNodes, now())
        val payloadSha256 = canonical.payloadSha256
        val bytes = canonical.bytes
        check(bytes.size <= MAX_PAYLOAD_BYTES) { "EXPORT_LIMIT_EXCEEDED" }
        val bundleSha256 = sha256(bytes)
        val path = storageManager.expectedAuditExportPath(exportId)
        val relative = "audit-exports/${path.fileName}"
        val export = AuditExportEntity(
            auditExportId = exportId,
            scopeType = scopeType,
            scopeJson = canonicalText(scope),
            filterJson = "{}",
            state = AuditExportState.PREPARING.name,
            stagingName = relative,
            payloadSha256 = payloadSha256,
            bundleSha256 = bundleSha256,
            sizeBytes = bytes.size.toLong(),
            recordCount = records.size,
            errorCode = null,
            createdAt = now(),
            updatedAt = now(),
        )
        storageDao.upsertAuditExport(export)
        val reservation = storageManager.reserveWrite("AUDIT_EXPORT", exportId, bytes.size.toLong())
        val part = path.resolveSibling("${path.fileName}.part")
        try {
            withContext(Dispatchers.IO) {
                Files.createDirectories(path.parent)
                val directory = Files.readAttributes(
                    path.parent,
                    BasicFileAttributes::class.java,
                    LinkOption.NOFOLLOW_LINKS,
                )
                check(directory.isDirectory && !directory.isSymbolicLink) { "Audit staging directory is unsafe." }
                Files.deleteIfExists(part)
                FileOutputStream(part.toFile(), false).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
                try {
                    Files.move(part, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(part, path, StandardCopyOption.REPLACE_EXISTING)
                }
                check(Files.size(path) == bytes.size.toLong() && sha256(path) == bundleSha256)
            }
            storageManager.recordPresentAndConsume(reservation, bytes.size.toLong(), bundleSha256)
            val staged = export.copy(state = AuditExportState.STAGED.name, updatedAt = now())
            storageDao.upsertAuditExport(staged)
            staged.toResult()
        } catch (failure: Throwable) {
            val partFailure = runCatching {
                withContext(NonCancellable + Dispatchers.IO) { Files.deleteIfExists(part) }
            }.exceptionOrNull()
            val finalMayExist = runCatching {
                Files.exists(path, LinkOption.NOFOLLOW_LINKS)
            }.getOrDefault(true)
            val reservationFailure = runCatching {
                withContext(NonCancellable) { storageManager.recordDownloadFailure(reservation, finalMayExist) }
            }.exceptionOrNull()
            val persistenceFailure = runCatching {
                withContext(NonCancellable) {
                    storageDao.upsertAuditExport(
                        export.copy(state = AuditExportState.FAILED.name, errorCode = "STAGING_FAILED", updatedAt = now()),
                    )
                }
            }.exceptionOrNull()
            listOfNotNull(partFailure, reservationFailure, persistenceFailure).forEach(failure::addSuppressed)
            throw failure
        }
    }

    private suspend fun buildRecords(selectedIds: Set<String>): List<AuditRecord> {
        val all = managedAppDao.getRegisteredAppRecords()
        val records = if (selectedIds.isEmpty()) all else all.filter { it.app.registeredAppId in selectedIds }
        check(selectedIds.isEmpty() || records.map { it.app.registeredAppId }.toSet() == selectedIds) {
            "An audit export app was not found."
        }
        val result = mutableListOf<AuditRecord>()
        val comparisonJobIds = records.flatMap { record ->
            record.comparisons.flatMap { listOfNotNull(it.runnerJobId, it.repeatRunnerJobId) }
        }.toSet()
        val jobs = jobDao.getJobRecords().filter { it.job.jobId in comparisonJobIds }.associateBy { it.job.jobId }
        val resourceIds = mutableSetOf<String>()

        records.forEach { record ->
            result += record("managed-app", record.app.registeredAppId, payload = buildJsonObject {
                put("registeredAppId", record.app.registeredAppId)
                put("displayName", record.app.displayName)
                put("canonicalRepositoryUrl", record.app.canonicalRepositoryUrl)
                put("provider", record.app.provider)
                put("managementMode", record.app.managementMode)
                put("installationSource", record.app.installationSource)
                put("createdAt", record.app.createdAt)
                put("updatedAt", record.app.updatedAt)
            })
            record.repositoryBinding?.let { binding ->
                result += record("repository-binding", binding.registeredAppId, payload = buildJsonObject {
                    put("registeredAppId", binding.registeredAppId)
                    put("provider", binding.provider)
                    put("instance", binding.instance)
                    binding.providerRepositoryId?.let { put("providerRepositoryId", it) }
                    put("identityStatus", binding.identityStatus)
                    put("registrationSlot", binding.registrationSlot)
                    binding.verifiedAt?.let { put("verifiedAt", it) }
                })
            }
            record.sourceDiscoveries.forEach { discovery ->
                result += record("source-discovery", discovery.discoveryId, payload = buildJsonObject {
                    put("discoveryId", discovery.discoveryId)
                    put("registeredAppId", discovery.registeredAppId)
                    put("provider", discovery.repositoryProvider)
                    put("instance", discovery.repositoryInstance)
                    put("providerRepositoryId", discovery.providerRepositoryId)
                    put("requestedBranch", discovery.requestedBranch)
                    discovery.resolvedCommitSha?.let { put("resolvedCommitSha", it) }
                    discovery.rootTreeSha?.let { put("rootTreeSha", it) }
                    put("state", discovery.state)
                    discovery.reason?.let { put("reason", it) }
                    put("entryCount", discovery.entryCount.toString())
                    put("requestCount", discovery.requestCount)
                    put("receivedBytes", discovery.receivedBytes.toString())
                    put("candidateCount", discovery.candidateCount)
                    put("startedAt", discovery.startedAt)
                    discovery.finishedAt?.let { put("finishedAt", it) }
                })
            }
            record.buildConfigurations.forEach { configuration ->
                result += record("build-configuration", "${configuration.registeredAppId}:${configuration.revision}", configuration.revision, buildJsonObject {
                    put("registeredAppId", configuration.registeredAppId)
                    put("revision", configuration.revision.toString())
                    put("schemaVersion", configuration.schemaVersion)
                    put("contentSha256", configuration.contentSha256)
                    put("validationState", configuration.validationState)
                    put("createdAt", configuration.createdAt)
                })
            }
            record.releases.forEach { release ->
                val snapshot = release.snapshot
                result += record("release-observation", snapshot.releaseSnapshotId, payload = buildJsonObject {
                    put("releaseSnapshotId", snapshot.releaseSnapshotId)
                    put("registeredAppId", snapshot.registeredAppId)
                    put("providerReleaseId", snapshot.providerReleaseId.toString())
                    put("tagName", snapshot.tagName)
                    put("resolvedCommitSha", snapshot.resolvedCommitSha)
                    put("releaseName", snapshot.releaseName)
                    put("releaseUrl", snapshot.releaseUrl)
                    put("targetCommitishRaw", snapshot.targetCommitishRaw)
                    put("draft", snapshot.isDraft)
                    put("prerelease", snapshot.isPrerelease)
                    put("immutable", snapshot.isImmutable)
                    put("createdAt", snapshot.releaseCreatedAt)
                    put("publishedAt", snapshot.publishedAt)
                    put("fetchedAt", snapshot.fetchedAt)
                    put("lastObservedAt", snapshot.lastObservedAt)
                    put("observationSha256", snapshot.observationSha256)
                })
                release.assets.forEach { asset ->
                    resourceIds += asset.releaseAssetId
                    result += record("asset-observation", asset.releaseAssetId, payload = buildJsonObject {
                        put("releaseAssetId", asset.releaseAssetId)
                        put("releaseSnapshotId", asset.releaseSnapshotId)
                        put("providerAssetId", asset.providerAssetId.toString())
                        put("name", asset.assetName)
                        put("selectionReason", asset.selectionReason)
                        put("contentType", asset.contentType)
                        put("providerSizeBytes", asset.providerSizeBytes.toString())
                        asset.providerDigestSha256?.let { put("providerDigestSha256", it) }
                        put("downloadStatus", asset.downloadStatus)
                        asset.downloadedSizeBytes?.let { put("downloadedSizeBytes", it.toString()) }
                        asset.computedRawSha256?.let { put("computedRawSha256", it) }
                        asset.packageName?.let { put("packageName", it) }
                        asset.versionName?.let { put("versionName", it) }
                        asset.versionCode?.let { put("versionCode", it.toString()) }
                        asset.signingCertificateSha256?.let { put("signingCertificateSha256", it) }
                        asset.currentSignerSha256?.let { put("currentSignerSha256", it) }
                        put("comparisonEligibility", asset.comparisonEligibility)
                        asset.incomparableReason?.let { put("incomparableReason", it) }
                        asset.downloadedAt?.let { put("downloadedAt", it) }
                    })
                }
            }
            record.comparisons.forEach { comparison ->
                resourceIds += listOfNotNull(comparison.localArtifactId, comparison.repeatLocalArtifactId)
                result += comparisonRecord(comparison)
                result += rawAxisRecords(comparison)
                listOfNotNull(comparison.runnerJobId, comparison.repeatRunnerJobId).forEach { jobId ->
                    val jobRecord = jobs[jobId] ?: return@forEach
                    jobRecord.buildEnvironmentManifest?.let { manifest ->
                        check(storedSandboxManifestValid(jobRecord.job, manifest.manifest)) {
                            "Stored public build evidence is invalid."
                        }
                        result += publicManifestRecord(jobRecord.job.jobId, manifest)
                        manifest.manifest.sandboxJson?.let { result += publicSandboxRecord(jobRecord.job.jobId, it) }
                    }
                    jobRecord.sourceScan?.let { result += publicScanRecord(jobRecord.job.jobId, it) }
                    jobRecord.installAttempts.forEach { attempt ->
                        result += record("install-attempt", attempt.attemptId, payload = buildJsonObject {
                            put("attemptId", attempt.attemptId)
                            put("source", "RUNNER_ARTIFACT")
                            put("jobId", attempt.jobId)
                            put("artifactId", attempt.artifactId)
                            put("status", attempt.status)
                            attempt.packageInstallerStatus?.let { put("packageInstallerStatus", it) }
                            put("createdAt", attempt.createdAt)
                            put("updatedAt", attempt.updatedAt)
                        })
                    }
                }
            }
            record.releaseInstallAttempts.forEach { attempt ->
                result += record("install-attempt", attempt.attemptId, payload = buildJsonObject {
                    put("attemptId", attempt.attemptId)
                    put("source", "OFFICIAL_RELEASE")
                    put("registeredAppId", attempt.registeredAppId)
                    put("releaseAssetId", attempt.releaseAssetId)
                    put("status", attempt.status)
                    attempt.packageInstallerStatus?.let { put("packageInstallerStatus", it) }
                    put("createdAt", attempt.createdAt)
                    put("updatedAt", attempt.updatedAt)
                })
            }
        }

        storageDao.getAvailability().filter { it.resourceId in resourceIds }.forEach { availability ->
            result += record("availability", "${availability.resourceKind}:${availability.resourceId}", payload = buildJsonObject {
                put("ownerType", availability.ownerType)
                put("ownerId", availability.ownerId)
                put("resourceKind", availability.resourceKind)
                put("resourceId", availability.resourceId)
                put("state", availability.state)
                availability.observedBytes?.let { put("observedBytes", it.toString()) }
                availability.knownSha256?.let { put("knownSha256", it) }
                availability.lastUsedAt?.let { put("lastUsedAt", it) }
                put("checkedAt", availability.checkedAt)
                availability.deletionRunId?.let { put("deletionRunId", it) }
                availability.deletionReason?.let { put("deletionReason", it) }
            })
        }
        val cleanupItems = storageDao.getCleanupItems().filter { it.resourceId in resourceIds }
        val cleanupRunIds = cleanupItems.mapTo(mutableSetOf()) { it.cleanupRunId }
        storageDao.getCleanupRuns().filter { it.cleanupRunId in cleanupRunIds }.forEach { run ->
            result += record("cleanup-run", run.cleanupRunId, payload = buildJsonObject {
                put("cleanupRunId", run.cleanupRunId)
                put("ownerType", run.ownerType)
                put("ownerId", run.ownerId)
                put("area", run.area)
                put("state", run.state)
                put("truncated", run.truncated)
                put("releasedBytes", run.releasedBytes.toString())
                run.startedAt?.let { put("startedAt", it) }
                run.finishedAt?.let { put("finishedAt", it) }
                put("createdAt", run.createdAt)
            })
        }
        cleanupItems.forEach { item ->
            result += record("cleanup-item", item.itemId, payload = buildJsonObject {
                put("itemId", item.itemId)
                put("cleanupRunId", item.cleanupRunId)
                put("resourceKind", item.resourceKind)
                put("resourceId", item.resourceId)
                put("observedBytes", item.observedBytes.toString())
                put("eligibleAt", item.eligibleAt)
                put("protectionReasons", JsonArray(item.protectionReasons.split(',').filter(String::isNotBlank).sorted().map(::jsonString)))
                put("selected", item.selected)
                item.result?.let { put("result", it) }
                put("releasedBytes", item.releasedBytes.toString())
                item.reasonCode?.let { put("reasonCode", it) }
            })
        }
        return result
    }

    private fun comparisonRecord(comparison: ComparisonRunEntity) = record(
        "comparison-attempt",
        comparison.comparisonRunId,
        payload = buildJsonObject {
            put("comparisonRunId", comparison.comparisonRunId)
            put("registeredAppId", comparison.registeredAppId)
            put("releaseSnapshotId", comparison.releaseSnapshotId)
            put("referenceAssetId", comparison.referenceAssetId)
            put("runnerJobId", comparison.runnerJobId)
            comparison.localArtifactId?.let { put("localArtifactId", it) }
            put("expectedCommitSha", comparison.expectedCommitSha)
            comparison.runnerResolvedCommitSha?.let { put("runnerResolvedCommitSha", it) }
            put("expectedRecipeId", comparison.expectedRecipeId)
            put("expectedVariantName", comparison.expectedVariantName)
            put("status", comparison.status)
            put("outcome", comparison.outcome)
            put("protocolVersion", comparison.protocolVersion)
            comparison.repeatRunnerJobId?.let { put("repeatRunnerJobId", it) }
            comparison.repeatLocalArtifactId?.let { put("repeatLocalArtifactId", it) }
            put("repeatOfficialOutcome", comparison.repeatOfficialOutcome)
            put("repeatabilityOutcome", comparison.repeatabilityOutcome)
            comparison.incomparableReason?.let { put("incomparableReason", it) }
            comparison.repeatIncomparableReason?.let { put("repeatIncomparableReason", it) }
            put("createdAt", comparison.createdAt)
            put("updatedAt", comparison.updatedAt)
            comparison.completedAt?.let { put("completedAt", it) }
        },
    )

    private fun rawAxisRecords(comparison: ComparisonRunEntity): List<AuditRecord> = listOf(
        "OFFICIAL_PRIMARY" to comparison.outcome,
        "OFFICIAL_REPEAT" to comparison.repeatOfficialOutcome,
        "LOCAL_REPEATABILITY" to comparison.repeatabilityOutcome,
    ).map { (axis, outcome) ->
        record("raw-comparison-axis", "${comparison.comparisonRunId}:$axis", payload = buildJsonObject {
            put("comparisonRunId", comparison.comparisonRunId)
            put("axis", axis)
            put("outcome", outcome)
        })
    }

    private fun publicManifestRecord(
        jobId: String,
        manifest: com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies,
    ) = record("public-build-manifest", jobId, payload = buildJsonObject {
        put("jobId", jobId)
        put("schemaVersion", manifest.manifest.schemaVersion)
        put("commitSha", manifest.manifest.commitSha)
        put("javaVersion", manifest.manifest.javaVersion)
        put("javaVendor", manifest.manifest.javaVendor)
        put("gradleVersion", manifest.manifest.gradleVersion)
        put("androidSdkApiLevel", manifest.manifest.androidSdkApiLevel)
        put("buildToolsVersion", manifest.manifest.buildToolsVersion)
        put("apkSha256", manifest.manifest.apkSha256)
        manifest.manifest.sourceDateEpoch?.let { put("sourceDateEpoch", it.toString()) }
        put("noBuildCache", manifest.manifest.noBuildCache)
        manifest.manifest.fixedLocale?.let { put("fixedLocale", it) }
        put("dependencies", buildJsonArray {
            manifest.dependencies.sortedWith(compareBy({ it.fileName }, { it.sha256 })).forEach { dependency ->
                add(buildJsonObject { put("fileName", dependency.fileName); put("sha256", dependency.sha256) })
            }
        })
        put("retrievedAt", manifest.manifest.retrievedAt)
    })

    private fun publicSandboxRecord(jobId: String, encoded: String): AuditRecord {
        val evidence = decodeSandboxEvidence(encoded)
        return record("public-sandbox-summary", jobId, payload = buildJsonObject {
            put("jobId", jobId)
            put("mode", evidence.mode.name)
            if (evidence.mode == BuildSandboxMode.DOCKER) {
                put("profileId", requireNotNull(evidence.profileId))
                put("imageDigest", requireNotNull(evidence.imageDigest))
                put("platform", requireNotNull(evidence.platform))
                put("engineVersion", requireNotNull(evidence.engineVersion))
                put("networkMode", requireNotNull(evidence.networkMode))
                evidence.limits?.let { limits ->
                    put("limits", buildJsonObject {
                        put("cpuCount", limits.cpuCount)
                        put("cpuset", limits.cpuset)
                        put("memoryBytes", limits.memoryBytes.toString())
                        put("memorySwapBytes", limits.memorySwapBytes.toString())
                        put("pids", limits.pids)
                        put("tmpfsBytes", limits.tmpfsBytes.toString())
                    })
                }
                evidence.isolation?.let { isolation ->
                    put("isolation", buildJsonObject {
                        put("uid", isolation.uid)
                        put("gid", isolation.gid)
                        put("readOnlyRoot", isolation.readOnlyRoot)
                        put("capDropAll", isolation.capDropAll)
                        put("noNewPrivileges", isolation.noNewPrivileges)
                        put("seccomp", isolation.seccomp)
                        put("sdkReadOnly", isolation.sdkReadOnly)
                        put("jdkReadOnly", isolation.jdkReadOnly)
                        put("dockerSocketMounted", isolation.dockerSocketMounted)
                        put("jobDiskQuotaEnforced", isolation.jobDiskQuotaEnforced)
                    })
                }
            }
        })
    }

    private fun publicScanRecord(
        jobId: String,
        scan: com.sanka1610.reprodroid.data.local.SourceScanWithDetails,
    ): AuditRecord {
        check(scan.scan.schemaVersion == 1 && SHA256.matches(scan.scan.resultSha256)) { "Stored public scan evidence is invalid." }
        return record("public-source-scan-summary", jobId, payload = buildJsonObject {
            put("jobId", jobId)
            put("schemaVersion", scan.scan.schemaVersion)
            put("resolvedCommitSha", scan.scan.resolvedCommitSha)
            put("scannerVersion", scan.scan.scannerVersion)
            put("resultSha256", scan.scan.resultSha256)
            put("scannedFiles", scan.scan.scannedFiles)
            put("scannedBytes", scan.scan.scannedBytes.toString())
            put("skippedBinaryFiles", scan.scan.skippedBinaryFiles)
            put("skippedSymlinks", scan.scan.skippedSymlinks)
            put("findingCount", scan.scan.findingCount)
            put("requiresReview", scan.scan.requiresReview)
            put("reviewed", scan.scan.reviewed)
            put("detectorCounts", buildJsonArray {
                scan.detectorCounts.sortedBy { it.detectorId }.forEach { count ->
                    add(buildJsonObject { put("detectorId", count.detectorId); put("count", count.count) })
                }
            })
            put("retrievedAt", scan.scan.retrievedAt)
        })
    }

    private fun record(type: String, stableId: String, order: Long = 0, payload: JsonObject): AuditRecord {
        return AuditRecord(type, stableId, order, payload, AuditCanonical.recordSha256(type, payload))
    }

    private fun scopeJson(type: String, ids: Set<String>) = buildJsonObject {
        put("type", type)
        put("registeredAppIds", JsonArray(ids.sorted().map(::jsonString)))
    }

    private fun verifiedStage(export: AuditExportEntity): java.nio.file.Path {
        val path = storageManager.expectedAuditExportPath(export.auditExportId)
        check(export.stagingName == "audit-exports/${path.fileName}") { "Audit staging identity is invalid." }
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        check(attributes.isRegularFile && !attributes.isSymbolicLink && attributes.size() == export.sizeBytes)
        check(sha256(path) == export.bundleSha256) { "Audit staging content is invalid." }
        return path
    }

    private fun AuditExportEntity.toResult() = StagedAuditExport(
        auditExportId = auditExportId,
        suggestedName = "reprodroid-audit-$auditExportId.rdaudit.json",
        state = state,
        payloadSha256 = requireNotNull(payloadSha256),
        sizeBytes = requireNotNull(sizeBytes),
        recordCount = requireNotNull(recordCount),
        errorCode = errorCode,
    )

    private data class AuditRecord(
        val type: String,
        val stableId: String,
        val order: Long,
        val payload: JsonObject,
        val sha256: String,
    ) {
        fun node(): JsonObject = buildJsonObject {
            put("type", type)
            put("schemaVersion", 1)
            put("payload", payload)
            put("sha256", sha256)
        }
    }

    private fun canonicalText(value: JsonObject) = String(canonicalBytes(value), StandardCharsets.UTF_8)
    private fun canonicalBytes(value: JsonObject) = JsonCanonicalizer(value.toString()).encodedUTF8
    private fun jsonString(value: String) = kotlinx.serialization.json.JsonPrimitive(value)
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun sha256(path: java.nio.file.Path): String = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
    private fun now() = Instant.now().toString()
    private fun requireCanonicalUuid(value: String, field: String) = require(UUID_PATTERN.matches(value)) {
        "$field must be a canonical lowercase UUID."
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_RECORDS = 20_000
        const val MAX_PAYLOAD_BYTES = 32 * 1024 * 1024
        const val COPY_BUFFER_BYTES = 64 * 1024
        val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
