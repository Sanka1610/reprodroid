package com.sanka1610.reprodroid.ui.shared

import com.sanka1610.reprodroid.data.provider.GitHubRepository
import com.sanka1610.reprodroid.data.provider.CodebergRepository
import com.sanka1610.reprodroid.data.provider.displayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryDisplayTest {
    private val canonical = "https://github.com/example/project"

    @Test fun `provider spelling is separate from normalized identity`() {
        val github = GitHubRepository("Example", "Project")
        assertEquals(canonical, github.canonicalUrl)
        assertEquals("https://github.com/Example/Project", github.displayUrl)
        val codeberg = CodebergRepository("Example", "Project")
        assertEquals("https://codeberg.org/example/project", codeberg.canonicalUrl)
        assertEquals("https://codeberg.org/Example/Project", codeberg.displayUrl)
    }

    @Test fun `stored spelling is preferred`() {
        assertEquals("https://github.com/Example/Project", repositoryDisplayUrl("https://github.com/Example/Project", canonical, null))
    }

    @Test fun `old lowercase registrations can use the same repository release spelling`() {
        assertEquals("https://github.com/Example/Project", repositoryDisplayUrl(canonical, canonical, "https://github.com/Example/Project/releases/tag/v1"))
    }

    @Test fun `unrelated or invalid release URLs cannot change displayed owner`() {
        listOf("https://evil.test/Example/Project/releases/tag/v1", "https://github.com/Other/Project/releases/tag/v1", "https://User@github.com/Example/Project/releases/tag/v1", "http://github.com/Example/Project/releases/tag/v1").forEach {
            assertEquals(canonical, repositoryDisplayUrl(canonical, canonical, it))
        }
    }
}
