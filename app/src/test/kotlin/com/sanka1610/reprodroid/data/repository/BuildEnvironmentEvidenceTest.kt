package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.BuildEnvironmentDependencyEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestEntity
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.JobEntity
import com.sanka1610.reprodroid.data.network.ArtifactMetadata
import com.sanka1610.reprodroid.data.network.BuildEnvironmentManifestResponse
import com.sanka1610.reprodroid.data.network.EffectiveBuild
import com.sanka1610.reprodroid.data.network.ExecutionMode
import com.sanka1610.reprodroid.data.network.JobResponse
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.PublicBuildDependency
import com.sanka1610.reprodroid.data.network.PublicJavaRuntime
import com.sanka1610.reprodroid.data.network.RequestedRevision
import com.sanka1610.reprodroid.data.network.RevisionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildEnvironmentEvidenceTest {
    @Test
    fun `validated response becomes deterministic Room rows`() {
        val validated = validateBuildEnvironmentManifest(
            jobId = "job-a",
            remoteJob = remoteJob("job-a"),
            response = response(
                listOf(
                    PublicBuildDependency("alpha.jar", "a".repeat(64)),
                    PublicBuildDependency("alpha.jar", "b".repeat(64)),
                ),
            ),
            retrievedAt = "2026-08-27T00:00:00Z",
        )

        assertEquals(COMMIT, validated.manifest.commitSha)
        assertEquals(listOf(0, 1), validated.dependencies.map { it.ordinal })
        assertEquals(listOf("a".repeat(64), "b".repeat(64)), validated.dependencies.map { it.sha256 })
    }

    @Test
    fun `response integrity rejects malformed hash unsafe name and non deterministic order`() {
        assertThrows(IllegalStateException::class.java) {
            validateBuildEnvironmentManifest(
                "job-a",
                remoteJob("job-a"),
                response(listOf(PublicBuildDependency("alpha.jar", "A".repeat(64)))),
                "2026-08-27T00:00:00Z",
            )
        }
        assertThrows(IllegalStateException::class.java) {
            validateBuildEnvironmentManifest(
                "job-a",
                remoteJob("job-a"),
                response(listOf(PublicBuildDependency("github_pat_private.jar", "a".repeat(64)))),
                "2026-08-27T00:00:00Z",
            )
        }
        assertThrows(IllegalStateException::class.java) {
            validateBuildEnvironmentManifest(
                "job-a",
                remoteJob("job-a"),
                response(
                    listOf(
                        PublicBuildDependency("zeta.jar", "a".repeat(64)),
                        PublicBuildDependency("alpha.jar", "b".repeat(64)),
                    ),
                ),
                "2026-08-27T00:00:00Z",
            )
        }
    }

    @Test
    fun `unique dependency hash difference is changed`() {
        val comparison = compareBuildEnvironments(
            job("job-a"),
            job("job-b", repositoryUrl = "https://github.com/morpheapp/microg-re"),
            storedManifest("job-a", listOf("library.jar" to "a".repeat(64))),
            storedManifest("job-b", listOf("library.jar" to "b".repeat(64))),
        )

        assertTrue(comparison.comparable)
        assertEquals(1, comparison.changedCount)
        assertEquals(0, comparison.buildAOnlyCount)
        assertEquals(0, comparison.buildBOnlyCount)
    }

    @Test
    fun `duplicate dependency names cancel exact matches without guessing changed`() {
        val comparison = compareBuildEnvironments(
            job("job-a"),
            job("job-b"),
            storedManifest(
                "job-a",
                listOf("library.jar" to "a".repeat(64), "library.jar" to "b".repeat(64)),
            ),
            storedManifest(
                "job-b",
                listOf("library.jar" to "a".repeat(64), "library.jar" to "c".repeat(64)),
            ),
        )

        assertEquals(1, comparison.sameCount)
        assertEquals(0, comparison.changedCount)
        assertEquals(1, comparison.buildAOnlyCount)
        assertEquals(1, comparison.buildBOnlyCount)
    }

    @Test
    fun `different source identity is not compared`() {
        val comparison = compareBuildEnvironments(
            job("job-a"),
            job("job-b", repositoryUrl = "https://github.com/example/other"),
            storedManifest("job-a", emptyList()),
            storedManifest("job-b", emptyList()),
        )

        assertFalse(comparison.comparable)
        assertEquals("BUILD_SOURCE_IDENTITY_MISMATCH", comparison.reason)
    }

    private fun response(dependencies: List<PublicBuildDependency>) = BuildEnvironmentManifestResponse(
        schemaVersion = 1,
        commit = COMMIT,
        java = PublicJavaRuntime("18.0.2.1+1", "Eclipse Adoptium"),
        gradle = "8.14.3",
        androidSdk = 36,
        buildTools = "36.0.0",
        dependencies = dependencies,
        apkHash = APK_SHA,
    )

    private fun remoteJob(jobId: String) = JobResponse(
        jobId = jobId,
        executionMode = ExecutionMode.REAL_TRUSTED,
        repositoryUrl = REPOSITORY,
        requestedRevision = RequestedRevision(RevisionType.TAG, "6.1.4"),
        resolvedCommitSha = COMMIT,
        state = JobState.SUCCEEDED,
        progressPercent = 100,
        requiresConfirmation = false,
        effectiveBuild = EffectiveBuild("recipe", "defaultRelease", ".", 18, listOf("assemble")),
        latestLogSequence = 1,
        artifacts = listOf(ArtifactMetadata("artifact", "microg.apk", 1, APK_SHA, "", "", 0)),
        createdAt = "2026-08-27T00:00:00Z",
        updatedAt = "2026-08-27T00:01:00Z",
    )

    private fun job(jobId: String, repositoryUrl: String = REPOSITORY) = JobEntity(
        jobId = jobId,
        executionMode = ExecutionMode.REAL_TRUSTED.name,
        repositoryUrl = repositoryUrl,
        revisionType = RevisionType.TAG.name,
        revisionValue = "6.1.4",
        simulationOutcome = null,
        resolvedCommitSha = COMMIT,
        state = JobState.SUCCEEDED.name,
        progressPercent = 100,
        latestLogSequence = 1,
        errorCode = null,
        errorMessage = null,
        createdAt = "2026-08-27T00:00:00Z",
        updatedAt = "2026-08-27T00:01:00Z",
    )

    private fun storedManifest(jobId: String, dependencies: List<Pair<String, String>>) =
        BuildEnvironmentManifestWithDependencies(
            manifest = BuildEnvironmentManifestEntity(
                jobId = jobId,
                schemaVersion = 1,
                commitSha = COMMIT,
                javaVersion = "18.0.2.1+1",
                javaVendor = "Eclipse Adoptium",
                gradleVersion = "8.14.3",
                androidSdkApiLevel = 36,
                buildToolsVersion = "36.0.0",
                apkSha256 = APK_SHA,
                retrievedAt = "2026-08-27T00:02:00Z",
            ),
            dependencies = dependencies.mapIndexed { ordinal, dependency ->
                BuildEnvironmentDependencyEntity(jobId, ordinal, dependency.first, dependency.second)
            },
        )

    private companion object {
        const val REPOSITORY = "https://github.com/MorpheApp/MicroG-RE.git"
        const val COMMIT = "d8df10ab687a1c1ca05221634cfa46bad262023a"
        const val APK_SHA = "30de03caea3da52c9febbeebb5d7f0d3246811d288d81b522bb456da19e7b033"
    }
}
