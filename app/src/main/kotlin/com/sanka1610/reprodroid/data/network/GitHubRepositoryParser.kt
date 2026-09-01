package com.sanka1610.reprodroid.data.provider

import java.net.URI

class InvalidGitHubRepositoryException(message: String) : IllegalArgumentException(message)

object GitHubRepositoryParser {
    fun parse(repositoryUrl: String): GitHubRepository {
        val trimmed = repositoryUrl.trim()
        if (
            trimmed.toByteArray(Charsets.UTF_8).size > MAX_URL_BYTES ||
            trimmed.any { it == '\u0000' || it.isISOControl() || it.isSurrogate() } ||
            '%' in trimmed
        ) {
            throw InvalidGitHubRepositoryException("GitHub repository URL contains unsupported characters.")
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: throw InvalidGitHubRepositoryException("GitHub repository URL is not a valid URI.")
        if (
            uri.scheme?.lowercase() != "https" ||
            uri.host?.lowercase() != GITHUB_HOST ||
            uri.userInfo != null ||
            uri.port != -1 ||
            uri.query != null ||
            uri.fragment != null
        ) {
            throw InvalidGitHubRepositoryException(
                "Only public https://github.com/{owner}/{repository} URLs are supported.",
            )
        }
        val normalizedPath = uri.path.removeSuffix("/")
        val segments = normalizedPath.removePrefix("/").split('/')
        if (segments.size != 2) {
            throw InvalidGitHubRepositoryException(
                "GitHub repository URL must identify exactly one owner and repository.",
            )
        }
        val owner = segments[0]
        val repository = segments[1].removeSuffix(".git")
        if (
            owner in DOT_SEGMENTS || repository in DOT_SEGMENTS ||
            !VALID_COMPONENT.matches(owner) || !VALID_COMPONENT.matches(repository)
        ) {
            throw InvalidGitHubRepositoryException("GitHub owner or repository name is invalid.")
        }
        return GitHubRepository(owner = owner, name = repository)
    }

    private const val GITHUB_HOST = "github.com"
    private const val MAX_URL_BYTES = 4096
    private val DOT_SEGMENTS = setOf(".", "..")
    private val VALID_COMPONENT = Regex("[A-Za-z0-9_.-]+")
}
