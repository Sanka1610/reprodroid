package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.network.*
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class SandboxEvidenceTest {
    @Test fun `strict Job JSON rejects explicit null missing Docker properties and extra HOST fields`() {
        listOf(
            "null", "{}", "{\"mode\":\"OTHER\",\"origin\":\"NEW_JOB\"}",
            "{\"mode\":\"HOST\",\"origin\":\"NEW_JOB\",\"profileId\":null}",
            "{\"mode\":\"DOCKER\",\"origin\":\"NEW_JOB\",\"profileId\":\"docker-microg-v1\"}",
        ).forEach { sandbox ->
            assertThrows(Exception::class.java) { validateJobSandboxJson(SANDBOX_JSON.parseToJsonElement("{\"sandbox\":$sandbox}").jsonObject) }
        }
        validateJobSandboxJson(SANDBOX_JSON.parseToJsonElement("{}").jsonObject)
    }

    @Test fun `immutable selection rejects disappearance mode and profile changes but permits cleanup progression`() {
        val previous = remote().toJobEntity(null, 0)
        assertThrows(Exception::class.java) { validateSandboxRefresh(previous, remote().copy(sandbox = null)) }
        assertThrows(Exception::class.java) { validateSandboxRefresh(previous, remote().copy(sandbox = JobSandbox(BuildSandboxMode.HOST, SandboxOrigin.NEW_JOB))) }
        assertThrows(Exception::class.java) { validateSandboxRefresh(previous, remote().copy(sandbox = dockerSelection().copy(profileId = "other"))) }
        validateSandboxRefresh(previous.copy(state = JobState.CANCELLED.name), remote().copy(state = JobState.CANCELLED, sandbox = dockerSelection().copy(cleanupStatus = SandboxCleanupStatus.PENDING)))
    }

    @Test fun `legacy absence differs from an unfetched new Job placeholder`() {
        val legacy = remote().copy(sandbox = null).toJobEntity(null, 0)
        assertThrows(Exception::class.java) { validateSandboxRefresh(legacy, remote()) }
        validateSandboxRefresh(legacy, remote().copy(sandbox = JobSandbox(BuildSandboxMode.HOST, SandboxOrigin.LEGACY_HOST)))
        validateSandboxRefresh(legacy.copy(sandboxResponseSeen = false), remote())
        assertTrue(sandboxSelectionText(legacy).contains("unavailable"))
    }

    @Test fun `schema three binds mode profile cleanup and immutable resource policy`() {
        val validated = validateBuildEnvironmentManifest("job", remote(), manifest(), "now")
        assertEquals(evidence(), decodeSandboxEvidence(requireNotNull(validated.manifest.sandboxJson)))
        assertThrows(Exception::class.java) { validateBuildEnvironmentManifest("job", remote().copy(sandbox = null), manifest(), "now") }
        assertThrows(Exception::class.java) { validateBuildEnvironmentManifest("job", remote(), manifest().copy(schemaVersion = 2, sandbox = null), "now") }
        assertThrows(Exception::class.java) { validateBuildEnvironmentManifest("job", remote(), manifest().copy(sandbox = evidence().copy(limits = evidence().limits!!.copy(cpuCount = 4))), "now") }
        assertThrows(Exception::class.java) { validateBuildEnvironmentManifest("job", remote().copy(sandbox = dockerSelection().copy(cleanupStatus = SandboxCleanupStatus.PENDING)), manifest(), "now") }
    }

    @Test fun `stored JSON cold start rejects unknown schema fields nulls and excessive engine text`() {
        val json = sandboxEvidenceJson(evidence())
        assertThrows(Exception::class.java) { decodeSandboxEvidence(json.dropLast(1) + ",\"unknown\":true}") }
        assertThrows(Exception::class.java) { decodeSandboxEvidence("{\"mode\":\"HOST\",\"limits\":null}") }
        assertThrows(Exception::class.java) { validateSandboxEvidence(evidence().copy(engineVersion = "a".repeat(129))) }
        val invalid = remote().toJobEntity(null, 0).copy(sandboxMode = "OTHER")
        assertFalse(sandboxAcknowledgementAllowed(invalid))
        assertTrue(sandboxSelectionText(invalid).contains("invalid"))
    }

    @Test fun `mixed HOST and Docker does not change dependency comparison`() {
        val dockerJob = remote()
        val hostJob = remote().copy(sandbox = JobSandbox(BuildSandboxMode.HOST, SandboxOrigin.NEW_JOB))
        val docker = validateBuildEnvironmentManifest("job", dockerJob, manifest(), "now")
        val host = validateBuildEnvironmentManifest("job", hostJob, manifest().copy(sandbox = SandboxEvidence(BuildSandboxMode.HOST)), "now")
        val comparison = compareBuildEnvironments(
            dockerJob.toJobEntity(null, 0), hostJob.toJobEntity(null, 0),
            com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies(docker.manifest, docker.dependencies),
            com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies(host.manifest, host.dependencies),
        )
        assertTrue(comparison.comparable)
        assertTrue(comparison.differences.isEmpty())
    }

    private fun dockerSelection() = JobSandbox(BuildSandboxMode.DOCKER, SandboxOrigin.NEW_JOB, DOCKER_PROFILE_ID, SandboxCleanupStatus.COMPLETE)
    private fun evidence() = SandboxEvidence(BuildSandboxMode.DOCKER, DOCKER_PROFILE_ID, DOCKER_IMAGE_DIGEST, "linux/amd64", "29.6.2", "BRIDGE",
        SandboxLimits(8, "0-7", 8_589_934_592, 8_589_934_592, 1024, 1_073_741_824),
        SandboxIsolation(1000, 1000, true, true, true, "DEFAULT", true, true, false, false))
    private fun remote() = JobResponse(
        jobId = "job", executionMode = ExecutionMode.REAL_TRUSTED, repositoryUrl = "https://github.com/example/app",
        requestedRevision = RequestedRevision(RevisionType.TAG, "1.0"), resolvedCommitSha = "a".repeat(40),
        state = JobState.SUCCEEDED, progressPercent = 100, requiresConfirmation = false,
        effectiveBuild = EffectiveBuild(buildRoot = ".", tasks = listOf("assemble"), determinism = DeterminismOptions(noBuildCache = false)),
        latestLogSequence = 0, artifacts = listOf(ArtifactMetadata("apk", "app.apk", 1, "b".repeat(64), "", "", 0)),
        createdAt = "now", updatedAt = "now", sandbox = dockerSelection(),
    )
    private fun manifest() = BuildEnvironmentManifestResponse(3, "a".repeat(40), PublicJavaRuntime("18.0.2.1", "Eclipse Adoptium"), "8.14.3", 36, "36.0.0", emptyList(), "b".repeat(64), DeterminismOptions(noBuildCache = false), evidence())
}
