package com.sanka1610.reprodroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.R

@Composable
internal fun dependencyPinningLabel(value: String): String =
    dependencyPinningLabelResource(value)?.let { stringResource(it) }
        ?: stringResource(R.string.value_unknown_with_value, value)

internal fun dependencyPinningLabelResource(value: String): Int? = when (value) {
    "NONE" -> R.string.technical_dependency_none
    "LOCKFILE" -> R.string.technical_dependency_lockfile
    "LOCKFILE_OFFLINE" -> R.string.technical_dependency_lockfile_offline
    else -> null
}
