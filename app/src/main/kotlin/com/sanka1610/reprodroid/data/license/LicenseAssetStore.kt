package com.sanka1610.reprodroid.data.license

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

data class LicenseDocument(
    val title: String,
    val assetPath: String,
    val text: String,
)

/**
 * Reads the license documents shipped in the APK. The store has no network
 * fallback: the offline view must describe exactly what this APK contains.
 */
class LicenseAssetStore(private val context: Context) {
    suspend fun loadDocuments(): List<LicenseDocument> = withContext(Dispatchers.IO) {
        DOCUMENTS.map { descriptor ->
            LicenseDocument(
                title = descriptor.title,
                assetPath = descriptor.assetPath,
                text = context.assets.open(descriptor.assetPath).bufferedReader(StandardCharsets.UTF_8).use {
                    it.readText()
                },
            )
        }
    }

    suspend fun loadReproDroidLicense(): List<LicenseDocument> =
        loadDocuments().filter { it.assetPath == REPRODROID_LICENSE_ASSET }

    suspend fun loadThirdPartyDocuments(): List<LicenseDocument> =
        loadDocuments().filterNot { it.assetPath == REPRODROID_LICENSE_ASSET }

    private data class Descriptor(val title: String, val assetPath: String)

    companion object {
        const val REPRODROID_LICENSE_ASSET = "licenses/LICENSE.txt"
        const val THIRD_PARTY_NOTICES_ASSET = "licenses/THIRD_PARTY_NOTICES.txt"
        const val SMALI_LICENSE_ASSET = "licenses/SMALI-DEXLIB2-LICENSE.txt"
        const val CHECKER_QUAL_LICENSE_ASSET = "licenses/CHECKER-QUAL-LICENSE.txt"
        const val SLF4J_LICENSE_ASSET = "licenses/SLF4J-LICENSE.txt"

        private val DOCUMENTS = listOf(
            Descriptor("ReproDroid license", REPRODROID_LICENSE_ASSET),
            Descriptor("Third-party notices", THIRD_PARTY_NOTICES_ASSET),
            Descriptor("smali-dexlib2 license", SMALI_LICENSE_ASSET),
            Descriptor("checker-qual license", CHECKER_QUAL_LICENSE_ASSET),
            Descriptor("slf4j-api license", SLF4J_LICENSE_ASSET),
        )
    }
}
