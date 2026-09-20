package com.sanka1610.reprodroid.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.ui.shared.statusLabel

@Composable
internal fun storageLabel(value: String): String = when (value) {
    "AVAILABLE" -> stringResource(R.string.storage_label_available)
    "UNAVAILABLE" -> stringResource(R.string.storage_label_unavailable)
    "INCOMPATIBLE" -> stringResource(R.string.storage_label_incompatible)
    "RUNNER_CHANGED" -> stringResource(R.string.storage_label_runner_changed)
    "OK" -> stringResource(R.string.storage_label_ok)
    "WARNING" -> stringResource(R.string.storage_label_warning)
    "OVER_BUDGET" -> stringResource(R.string.storage_label_over_budget)
    "STORAGE_UNAVAILABLE" -> stringResource(R.string.storage_label_storage_unavailable)
    "COMPLETE" -> stringResource(R.string.storage_label_complete)
    "INCOMPLETE" -> stringResource(R.string.storage_label_incomplete)
    "PARTIAL" -> stringResource(R.string.storage_label_partial)
    "PREVIEWED" -> stringResource(R.string.storage_label_previewed)
    "APPLYING" -> stringResource(R.string.storage_label_applying)
    "REJECTED" -> stringResource(R.string.storage_label_rejected)
    "RECONCILIATION_REQUIRED" -> stringResource(R.string.storage_label_reconciliation_required)
    "STAGED" -> stringResource(R.string.storage_label_staged)
    "COPYING" -> stringResource(R.string.storage_label_copying)
    "COPIED" -> stringResource(R.string.storage_label_copied)
    "DELETED" -> stringResource(R.string.storage_label_deleted)
    "ALREADY_MISSING" -> stringResource(R.string.storage_label_already_missing)
    "SKIPPED_PROTECTED" -> stringResource(R.string.storage_label_skipped_protected)
    "REFERENCE_APK" -> stringResource(R.string.storage_label_reference_apk)
    "REFERENCE_ICON" -> stringResource(R.string.storage_label_reference_icon)
    "RUNNER_APK" -> stringResource(R.string.storage_label_runner_apk)
    "AUDIT_EXPORT" -> stringResource(R.string.storage_label_audit_export)
    "CURRENT_RELEASE" -> stringResource(R.string.storage_label_current_release)
    "CURRENT_COMPARISON_REFERENCE" -> stringResource(R.string.storage_label_current_comparison_reference)
    "CURRENT_COMPARISON_ARTIFACT" -> stringResource(R.string.storage_label_current_comparison_artifact)
    "DOWNLOAD_ACTIVE" -> stringResource(R.string.storage_label_download_active)
    "INSTALL_ACTIVE" -> stringResource(R.string.storage_label_install_active)
    "COMPARISON_ACTIVE" -> stringResource(R.string.storage_label_comparison_active)
    "ACTIVE_RESERVATION" -> stringResource(R.string.storage_label_active_reservation)
    "EXPORT_ACTIVE" -> stringResource(R.string.storage_label_export_active)
    "RESOURCE_CHANGED" -> stringResource(R.string.storage_label_resource_changed)
    "PRESENT" -> stringResource(R.string.storage_label_present)
    "MISSING" -> stringResource(R.string.storage_label_missing)
    "CORRUPT" -> stringResource(R.string.storage_label_corrupt)
    else -> statusLabel(value)
}
