package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.ExistingInstallStatus
import com.sanka1610.reprodroid.data.local.TrustLevel

object PrivilegedInstallPolicy {
    fun isEligible(
        requiresRiskConfirmation: Boolean,
        existingInstallStatus: String?,
        trustLevel: TrustLevel?,
    ): Boolean = !requiresRiskConfirmation && (
        existingInstallStatus == ExistingInstallStatus.SIGNER_MATCH.name ||
            trustLevel == TrustLevel.REPRODUCIBLE
        )
}
