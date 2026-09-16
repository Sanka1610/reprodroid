package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.ExistingInstallStatus
import com.sanka1610.reprodroid.data.local.TrustLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedInstallPolicyTest {
    @Test
    fun signerMatchedUpdateIsEligibleWithoutRiskWarning() {
        assertTrue(
            PrivilegedInstallPolicy.isEligible(
                requiresRiskConfirmation = false,
                existingInstallStatus = ExistingInstallStatus.SIGNER_MATCH.name,
                trustLevel = TrustLevel.BUILDABLE,
            ),
        )
    }

    @Test
    fun reproducibleNewInstallIsEligibleWithoutRiskWarning() {
        assertTrue(
            PrivilegedInstallPolicy.isEligible(
                requiresRiskConfirmation = false,
                existingInstallStatus = ExistingInstallStatus.NOT_INSTALLED_OR_NOT_VISIBLE.name,
                trustLevel = TrustLevel.REPRODUCIBLE,
            ),
        )
    }

    @Test
    fun riskWarningAlwaysForcesSystemInstaller() {
        assertFalse(
            PrivilegedInstallPolicy.isEligible(
                requiresRiskConfirmation = true,
                existingInstallStatus = ExistingInstallStatus.SIGNER_MATCH.name,
                trustLevel = TrustLevel.REPRODUCIBLE,
            ),
        )
    }

    @Test
    fun acquisitionNewInstallWithoutReproducibilityIsNotEligible() {
        assertFalse(
            PrivilegedInstallPolicy.isEligible(
                requiresRiskConfirmation = false,
                existingInstallStatus = ExistingInstallStatus.NOT_INSTALLED_OR_NOT_VISIBLE.name,
                trustLevel = TrustLevel.BUILDABLE,
            ),
        )
    }
}
