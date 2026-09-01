package com.sanka1610.reprodroid.ui

internal class PreviewGenerationGate {
    private var currentGeneration = 0L

    val generation: Long
        get() = currentGeneration

    fun begin(requestedUrl: String): PreviewRequestToken =
        PreviewRequestToken(++currentGeneration, requestedUrl)

    fun invalidate(): Long = ++currentGeneration

    fun isCurrent(token: PreviewRequestToken): Boolean =
        token.generation == currentGeneration
}

internal data class PreviewRequestToken(
    val generation: Long,
    val requestedUrl: String,
)
