package com.sanka1610.reprodroid.ui

internal fun dependencyPinningLabel(value: String): String = when (value) {
    "NONE" -> "None"
    "LOCKFILE" -> "Lockfile checked"
    "LOCKFILE_OFFLINE" -> "Lockfile checked · Gradle offline resolution"
    else -> "Unknown ($value)"
}
