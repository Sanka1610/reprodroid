package com.sanka1610.reprodroid.data.provider

import io.ktor.http.Headers
import java.time.Instant

/**
 * Codeberg exposes RFC 9446-style quota headers rather than GitHub's epoch
 * based X-RateLimit-* headers.  The parser is deliberately strict: a partial
 * or malformed header is not evidence that a request was rate limited.
 */
internal data class CodebergRateLimitEvidence(
    val remaining: Long?,
    val retryNotBefore: Instant?,
    val exhausted: Boolean,
)

internal fun codebergRateLimitEvidence(headers: Headers, now: Instant): CodebergRateLimitEvidence {
    val policy = parsePolicyEntries(headers["ratelimit-policy"])
    val rate = parseRateEntries(headers["ratelimit"])
    if (policy == null || rate == null || policy.isEmpty() || rate.isEmpty()) {
        return CodebergRateLimitEvidence(remaining = null, retryNotBefore = null, exhausted = false)
    }
    val policyNames = policy.mapTo(hashSetOf()) { it.name }
    val matching = rate.filter { it.name in policyNames }
    if (matching.isEmpty()) {
        return CodebergRateLimitEvidence(remaining = null, retryNotBefore = null, exhausted = false)
    }
    val exhausted = matching.filter { it.remaining == 0L }
    val maxRetrySeconds = exhausted.maxOfOrNull { it.resetAfterSeconds }
    val retryNotBefore = maxRetrySeconds?.let { seconds ->
        runCatching { now.plusSeconds(seconds) }.getOrNull()
    }
    return CodebergRateLimitEvidence(
        remaining = matching.minOfOrNull { it.remaining },
        retryNotBefore = retryNotBefore,
        exhausted = exhausted.isNotEmpty(),
    )
}

private data class PolicyEntry(val name: String, val quota: Long, val windowSeconds: Long)
private data class RateEntry(val name: String, val remaining: Long, val resetAfterSeconds: Long)

private data class ParsedItem(val name: String, val parameters: Map<String, String>)

private val QUOTED_NAME = Regex("\\\"([^\\\"\\p{Cntrl}]+)\\\"")
private val PARAMETER_NAME = Regex("[A-Za-z][A-Za-z0-9_-]*")
private val DECIMAL = Regex("[0-9]+")

private fun parseItems(value: String?): List<ParsedItem>? {
    if (value == null) return null
    return value.split(',').mapNotNull { raw -> parseItem(raw.trim()) }
}

private fun parseItem(value: String): ParsedItem? {
    if (value.isEmpty()) return null
    val fields = value.split(';').map(String::trim)
    val name = QUOTED_NAME.matchEntire(fields.firstOrNull().orEmpty())?.groupValues?.get(1) ?: return null
    val parameters = linkedMapOf<String, String>()
    for (field in fields.drop(1)) {
        val separator = field.indexOf('=')
        if (separator <= 0 || separator == field.lastIndex) return null
        val key = field.substring(0, separator).trim().lowercase()
        val parameterValue = field.substring(separator + 1).trim()
        if (!PARAMETER_NAME.matches(key) || parameters.put(key, parameterValue) != null) return null
    }
    return ParsedItem(name, parameters)
}

private fun decimal(value: String?): Long? = value
    ?.takeIf(DECIMAL::matches)
    ?.toLongOrNull()

private fun parsePolicyEntries(value: String?): List<PolicyEntry>? = parseItems(value)?.mapNotNull { item ->
    val quota = decimal(item.parameters["q"]) ?: return@mapNotNull null
    val window = decimal(item.parameters["w"]) ?: return@mapNotNull null
    if (quota <= 0 || window <= 0) return@mapNotNull null
    PolicyEntry(item.name, quota, window)
}

private fun parseRateEntries(value: String?): List<RateEntry>? = parseItems(value)?.mapNotNull { item ->
    val remaining = decimal(item.parameters["r"]) ?: return@mapNotNull null
    val reset = decimal(item.parameters["t"]) ?: return@mapNotNull null
    RateEntry(item.name, remaining, reset)
}
