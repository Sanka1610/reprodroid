package com.sanka1610.reprodroid.data.provider

import java.net.URI

class InvalidCodebergRepositoryException(message: String) : IllegalArgumentException(message)

data class CodebergRepository(
    override val owner: String,
    override val name: String,
) : ProviderRepositoryLocator {
    override val host: String = "codeberg.org"
    override val canonicalUrl: String = "https://codeberg.org/${owner.lowercase()}/${name.lowercase()}"
}

object CodebergRepositoryParser {
    fun parse(repositoryUrl: String): CodebergRepository {
        val trimmed = repositoryUrl.trim()
        if (
            trimmed.toByteArray(Charsets.UTF_8).size > MAX_URL_BYTES ||
            trimmed.any { it == '\u0000' || it.isISOControl() || it.isSurrogate() } ||
            '%' in trimmed
        ) {
            throw InvalidCodebergRepositoryException("Codeberg repository URL contains unsupported characters.")
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: throw InvalidCodebergRepositoryException("Codeberg repository URL is not a valid URI.")
        if (
            uri.scheme?.lowercase() != "https" ||
            uri.host?.lowercase() != HOST ||
            uri.userInfo != null ||
            uri.port != -1 ||
            uri.query != null ||
            uri.fragment != null
        ) {
            throw InvalidCodebergRepositoryException(
                "Only public https://codeberg.org/{owner}/{repository} URLs are supported.",
            )
        }
        val normalizedPath = uri.path.removeSuffix("/")
        val segments = normalizedPath.removePrefix("/").split('/')
        if (segments.size != 2) {
            throw InvalidCodebergRepositoryException(
                "Codeberg repository URL must identify exactly one owner and repository.",
            )
        }
        val owner = segments[0]
        val repository = segments[1].removeSuffix(".git")
        if (
            owner in DOT_SEGMENTS || repository in DOT_SEGMENTS ||
            !VALID_COMPONENT.matches(owner) || !VALID_COMPONENT.matches(repository)
        ) {
            throw InvalidCodebergRepositoryException("Codeberg owner or repository name is invalid.")
        }
        return CodebergRepository(owner = owner, name = repository)
    }

    private const val HOST = "codeberg.org"
    private const val MAX_URL_BYTES = 4096
    private val DOT_SEGMENTS = setOf(".", "..")
    private val VALID_COMPONENT = Regex("[A-Za-z0-9_.-]+")
}

object RepositoryProviderParsers {
    fun parse(repositoryUrl: String): ProviderRepositoryLocator {
        val host = runCatching { URI(repositoryUrl.trim()).host?.lowercase() }.getOrNull()
        return when (host) {
            "github.com" -> GitHubRepositoryParser.parse(repositoryUrl)
            "codeberg.org" -> CodebergRepositoryParser.parse(repositoryUrl)
            else -> throw IllegalArgumentException(
                "Only public GitHub and Codeberg repository URLs are supported.",
            )
        }
    }
}

internal fun isExactCodebergReleaseDownloadUrl(
    repository: CodebergRepository,
    tagName: String,
    assetName: String,
    value: String,
): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    val segments = uri.path?.removePrefix("/")?.split('/') ?: return false
    val expected = listOf(repository.owner, repository.name, "releases", "download", tagName, assetName)
    return uri.scheme?.lowercase() == "https" &&
        uri.host?.lowercase() == "codeberg.org" &&
        uri.userInfo == null &&
        uri.port == -1 &&
        uri.query == null &&
        uri.fragment == null &&
        !Regex("%(?:2f|5c|2e)", RegexOption.IGNORE_CASE).containsMatchIn(uri.rawPath.orEmpty()) &&
        segments == expected &&
        segments.none { segment ->
            segment.isEmpty() || segment == "." || segment == ".." || segment.any(Char::isISOControl)
        }
}
