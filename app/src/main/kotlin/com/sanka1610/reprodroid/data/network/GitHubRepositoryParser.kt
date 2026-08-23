package com.sanka1610.reprodroid.data.provider

import java.net.URI

class InvalidGitHubRepositoryException(message: String) : IllegalArgumentException(message)

object GitHubRepositoryParser {
    fun parse(repositoryUrl: String): GitHubRepository {
        val uri = runCatching { URI(repositoryUrl.trim()) }.getOrNull()
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
        val segments = uri.path.split('/').filter(String::isNotBlank)
        if (segments.size != 2) {
            throw InvalidGitHubRepositoryException(
                "GitHub repository URL must identify exactly one owner and repository.",
            )
        }
        val owner = segments[0]
        val repository = segments[1].removeSuffix(".git")
        if (!VALID_COMPONENT.matches(owner) || !VALID_COMPONENT.matches(repository)) {
            throw InvalidGitHubRepositoryException("GitHub owner or repository name is invalid.")
        }
        return GitHubRepository(owner = owner, name = repository)
    }

    private const val GITHUB_HOST = "github.com"
    private val VALID_COMPONENT = Regex("[A-Za-z0-9_.-]+")
}
