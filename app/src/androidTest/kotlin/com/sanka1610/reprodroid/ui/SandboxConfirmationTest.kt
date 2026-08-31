package com.sanka1610.reprodroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sanka1610.reprodroid.data.local.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production card with inert callbacks: no API acknowledgements, builds or installation. */
@RunWith(AndroidJUnit4::class)
class SandboxConfirmationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun independentBuildAndRetryCardsResetRceAcknowledgementAndDisplayTheirOwnMode() {
        val record = mutableStateOf(record("build-a", "DOCKER"))
        val confirmed = mutableListOf<String>()
        render(record, onConfirm = { confirmed += record.value.job.jobId })
        val button = compose.onNodeWithText("Confirm and run fixed build")
        compose.onNodeWithText("DOCKER / docker-microg-v1 — cleanup NOT_CREATED").assertExists()
        button.assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        button.assertIsEnabled().performScrollTo().performClick()
        assertEquals(listOf("build-a"), confirmed)
        compose.runOnIdle { record.value = record("build-b", "HOST") }
        compose.onNodeWithText("HOST — no build container").assertExists()
        button.assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsOff().performScrollTo().performClick()
        button.assertIsEnabled().performScrollTo().performClick()
        compose.runOnIdle { record.value = record("retry-new-job", "DOCKER") }
        button.assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsOff()
        assertEquals(listOf("build-a", "build-b"), confirmed)
    }

    @Test fun sourceReviewIsSeparateAndResetsWhenItsDigestOrJobChanges() {
        val record = mutableStateOf(record("build-a", "DOCKER"))
        val reviews = mutableListOf<String>()
        render(record, onReview = { reviews += it })
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.runOnIdle { record.value = review(record.value, "a".repeat(64)) }
        val button = compose.onNodeWithText("Acknowledge findings and continue")
        button.assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsOff().performScrollTo().performClick()
        button.assertIsEnabled().performScrollTo().performClick()
        assertEquals(listOf("a".repeat(64)), reviews)
        compose.runOnIdle { record.value = review(record.value, "b".repeat(64)) }
        button.assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsOff().performScrollTo().performClick()
        compose.runOnIdle { record.value = review(record("retry-new-job", "HOST"), "b".repeat(64)) }
        button.assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsOff()
    }

    @Test fun invalidSandboxWarningDisablesAnAlreadyCheckedConfirmation() {
        val record = mutableStateOf(record("build-a", "DOCKER"))
        val warning = mutableStateOf<String?>(null)
        render(record, warning)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Confirm and run fixed build").assertIsEnabled()
        compose.runOnIdle { warning.value = "Sandbox refresh invalid; last valid state retained" }
        compose.onNodeWithText("Confirm and run fixed build").assertIsNotEnabled()
    }

    private fun render(
        record: androidx.compose.runtime.State<JobRecord>,
        warning: androidx.compose.runtime.State<String?> = mutableStateOf(null),
        onConfirm: (String) -> Unit = {},
        onReview: (String) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    JobCard(record.value, {}, {}, onConfirm, emptySet(), {}, {}, null, {}, null,
                        warning.value, onReview)
                }
            }
        }
    }

    private fun record(id: String, mode: String) = JobRecord(
        job = JobEntity(
            jobId = id, executionMode = "REAL_TRUSTED", repositoryUrl = "https://github.com/example/app",
            revisionType = "TAG", revisionValue = "1.0", simulationOutcome = null,
            resolvedCommitSha = "a".repeat(40), requiresConfirmation = true,
            state = "AWAITING_CONFIRMATION", progressPercent = 10, latestLogSequence = 0,
            errorCode = null, errorMessage = null, createdAt = "now", updatedAt = "now",
            sandboxMode = mode, sandboxOrigin = "NEW_JOB", sandboxResponseSeen = true,
            sandboxProfileId = if (mode == "DOCKER") "docker-microg-v1" else null,
            sandboxCleanupStatus = if (mode == "DOCKER") "NOT_CREATED" else null,
        ), artifacts = emptyList(), logs = emptyList(), installAttempts = emptyList(),
    )

    private fun review(record: JobRecord, digest: String) = record.copy(
        job = record.job.copy(state = "AWAITING_SCAN_REVIEW", requiresConfirmation = false),
        sourceScan = SourceScanWithDetails(
            SourceScanEntity(record.job.jobId, 1, "a".repeat(40), "reprodroid-static-v1", digest,
                1, 10, 0, 0, 1, true, false, "now"),
            emptyList(), listOf(SourceScanFindingEntity(record.job.jobId, 0, "DYNAMIC_NATIVE_LOAD_API", "file.java", 1, 1)),
        ),
    )
}
