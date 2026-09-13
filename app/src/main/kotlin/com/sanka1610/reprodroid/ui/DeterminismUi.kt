package com.sanka1610.reprodroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.R
import java.time.Instant

@Composable
internal fun determinismSummary(
    sourceDateEpoch: Long?,
    noBuildCache: Boolean,
    fixedLocale: String?,
): String {
    if (sourceDateEpoch == null && !noBuildCache && fixedLocale == null) {
        return stringResource(R.string.technical_determinism_not_configured)
    }
    val parts = mutableListOf<String>()
    if (sourceDateEpoch != null) {
        val instant = sourceDateEpochInstant(sourceDateEpoch)
        parts += stringResource(
            R.string.technical_determinism_epoch,
            sourceDateEpoch,
            instant?.let { " ($it)" }.orEmpty(),
        )
    }
    if (noBuildCache) {
        parts += stringResource(R.string.technical_determinism_no_build_cache)
    }
    if (fixedLocale != null) {
        parts += stringResource(R.string.technical_determinism_locale, fixedLocale)
    }
    return parts.joinToString(" · ")
}

internal fun sourceDateEpochInstant(epoch: Long): String? =
    runCatching { Instant.ofEpochSecond(epoch).toString() }.getOrNull()
