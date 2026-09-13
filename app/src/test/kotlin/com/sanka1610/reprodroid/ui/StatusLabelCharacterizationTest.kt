package com.sanka1610.reprodroid.ui

import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.ui.shared.statusLabelResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusLabelCharacterizationTest {
    @Test
    fun aliasesResolveToTheCurrentSharedResources() {
        val expected = mapOf(
            "ACTIVE" to R.string.state_active,
            "INACTIVE" to R.string.state_inactive,
            "NOT_CHECKED" to R.string.state_not_checked,
            "NOT_EVALUATED" to R.string.state_not_checked,
            "CHECKING" to R.string.state_checking,
            "RESOLVING" to R.string.state_checking,
            "SCANNING_TREE" to R.string.state_checking,
            "PENDING" to R.string.state_pending,
            "QUEUED" to R.string.state_pending,
            "RUNNING" to R.string.state_running,
            "BUILDING" to R.string.state_running,
            "AVAILABLE" to R.string.state_success,
            "UP_TO_DATE" to R.string.state_success,
            "SUCCESS" to R.string.state_success,
            "COMPLETE" to R.string.state_success,
            "COMPLETED" to R.string.state_success,
            "UPDATE_AVAILABLE" to R.string.state_update_available,
            "NOT_INSTALLED" to R.string.state_not_installed,
            "REPRODUCIBLE" to R.string.state_reproducible,
            "EQUIVALENT" to R.string.state_reproducible,
            "MATCH" to R.string.state_match,
            "BUILDABLE" to R.string.state_buildable,
            "DIFFERENT" to R.string.state_different,
            "INCOMPARABLE" to R.string.state_incomparable,
            "FAILED" to R.string.state_error,
            "ERROR" to R.string.state_error,
            "CANCELLED" to R.string.state_cancelled,
            "INTERRUPTED" to R.string.state_interrupted,
            "AWAITING_ASSET_SELECTION" to R.string.state_awaiting_selection,
            "OLDER_THAN_INSTALLED" to R.string.state_older_than_installed,
            "UNKNOWN" to R.string.value_unknown,
        )

        expected.forEach { (value, resource) ->
            assertEquals(resource, statusLabelResource(value))
        }
        assertEquals(R.string.value_unknown, statusLabelResource(null))
    }

    @Test
    fun unrecognizedValuesRemainLiteralFallbacks() {
        assertNull(statusLabelResource("NEW_PROVIDER_STATE"))
    }
}
