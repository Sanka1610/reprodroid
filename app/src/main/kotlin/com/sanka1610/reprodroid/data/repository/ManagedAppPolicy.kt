package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.InstallationSource
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.UpdateStatus

internal fun validateModeAndInstallationSource(
    mode: ManagementMode,
    source: InstallationSource,
) {
    require(mode == ManagementMode.VERIFICATION || source == InstallationSource.OFFICIAL_RELEASE) {
        "Acquisition mode can only install the official release APK."
    }
}

internal fun canChangeInstallationSource(installedVersionCode: Long?): Boolean =
    installedVersionCode == null

internal fun evaluateUpdateStatus(
    candidateVersionCode: Long?,
    installedVersionCode: Long?,
): UpdateStatus = when {
    candidateVersionCode == null || candidateVersionCode <= 0 -> UpdateStatus.UNKNOWN
    installedVersionCode == null -> UpdateStatus.NOT_INSTALLED
    candidateVersionCode > installedVersionCode -> UpdateStatus.UPDATE_AVAILABLE
    candidateVersionCode == installedVersionCode -> UpdateStatus.UP_TO_DATE
    else -> UpdateStatus.OLDER_THAN_INSTALLED
}
