package com.sanka1610.reprodroid.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonRetryPolicyTest {
    @Test
    fun runnerTerminalFailuresAreRetryableWithoutChangingReferenceIdentity() {
        assertTrue(isRetryableRunnerFailureReason("RUNNER_JOB_FAILED"))
        assertTrue(isRetryableRunnerFailureReason("RUNNER_JOB_CANCELLED"))
        assertTrue(isRetryableRunnerFailureReason("RUNNER_JOB_INTERRUPTED"))
        assertTrue(isRetryableRunnerFailureReason("RUNNER_JOB_MISSING"))
    }

    @Test
    fun unrelatedIncomparableReasonsRemainFailClosed() {
        assertFalse(isRetryableRunnerFailureReason(null))
        assertFalse(isRetryableRunnerFailureReason("COMPARISON_PROFILE_NOT_SUPPORTED"))
        assertFalse(isRetryableRunnerFailureReason("RUNNER_REPOSITORY_MISMATCH"))
        assertFalse(isRetryableRunnerFailureReason("SOURCE_COMMIT_MISMATCH"))
        assertFalse(isRetryableRunnerFailureReason("LOCAL_ARTIFACT_COUNT_INVALID"))
        assertFalse(isRetryableRunnerFailureReason("RUNNER_JOB_FAILED_REPEAT"))
    }
}
