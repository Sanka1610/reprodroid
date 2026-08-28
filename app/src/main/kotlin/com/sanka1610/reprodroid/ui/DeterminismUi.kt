package com.sanka1610.reprodroid.ui

import java.time.Instant

internal fun determinismSummary(
    sourceDateEpoch: Long?,
    noBuildCache: Boolean,
    fixedLocale: String?,
): String {
    if (sourceDateEpoch == null && !noBuildCache && fixedLocale == null) return "Not configured"
    return buildList {
        sourceDateEpoch?.let { epoch ->
            val instant = runCatching { Instant.ofEpochSecond(epoch).toString() }.getOrNull()
            add("SOURCE_DATE_EPOCH $epoch${instant?.let { " ($it)" }.orEmpty()}")
        }
        if (noBuildCache) add("Gradle build cache disabled by Runner")
        fixedLocale?.let { add("process locale $it") }
    }.joinToString(" · ")
}
