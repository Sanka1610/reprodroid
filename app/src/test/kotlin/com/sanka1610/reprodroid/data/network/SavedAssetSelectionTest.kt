package com.sanka1610.reprodroid.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SavedAssetSelectionTest {
    @Test
    fun `multiple candidates require an explicit condition with exactly one match`() {
        val arm = candidate("1", "app-arm64-v8a-release.apk")
        val x86 = candidate("2", "app-x86_64-release.apk")
        assertNull(SavedAssetSelection.select(listOf(arm, x86), null))

        val encoded = SavedAssetSelection.encode(
            SavedAssetSelectionCondition(abi = "ARM64_V8A", variant = "RELEASE"),
        )
        val selected = SavedAssetSelection.select(listOf(x86, arm), encoded)
        assertEquals("1", selected?.asset?.id)
        assertEquals("SAVED_SELECTION_CONDITION", selected?.reason)
    }

    @Test
    fun `condition never persists a provider asset id and ambiguous match stays unselected`() {
        val encoded = SavedAssetSelection.encode(SavedAssetSelectionCondition(variant = "RELEASE"))
        assertNull(
            SavedAssetSelection.select(
                listOf(candidate("1", "one-release.apk"), candidate("2", "two-release.apk")),
                encoded,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            SavedAssetSelection.decode("{\"schemaVersion\":1,\"providerAssetId\":\"1\",\"exactFilename\":\"app.apk\"}")
        }
    }

    @Test
    fun `scheduled candidate reevaluation uses the same unique filename condition`() {
        val encoded = SavedAssetSelection.encode(
            SavedAssetSelectionCondition(exactFilename = "app-arm64-v8a-release.apk"),
        )

        assertEquals(
            "app-arm64-v8a-release.apk",
            SavedAssetSelection.selectFilename(
                listOf("app-x86_64-release.apk", "app-arm64-v8a-release.apk"),
                encoded,
            ),
        )
        assertNull(
            SavedAssetSelection.selectFilename(
                listOf("app-arm64-v8a-release.apk", "app-arm64-v8a-release.apk"),
                encoded,
            ),
        )
    }

    private fun candidate(id: String, name: String) = ProviderReleaseAssetCandidate(
        asset = object : ProviderReleaseAsset {
            override val id = id
            override val name = name
            override val contentType: String? = null
            override val size = 100L
            override val digest: String? = null
            override val browserDownloadUrl = "https://codeberg.org/example/app/releases/download/v1/$name"
            override val providerCreatedAt: String? = null
            override val providerAssetType: String? = "attachment"
        },
        providerSha256 = null,
    )
}
