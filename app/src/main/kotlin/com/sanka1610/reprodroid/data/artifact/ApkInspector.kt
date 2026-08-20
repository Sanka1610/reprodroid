package com.sanka1610.reprodroid.data.artifact

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.sanka1610.reprodroid.data.local.ExistingInstallStatus
import java.io.File
import java.security.MessageDigest

class ApkInspectionException(message: String) : RuntimeException(message)

data class ApkInspection(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val signingCertificateSha256: List<String>,
    val currentSignerSha256: List<String>,
    val existingInstallStatus: ExistingInstallStatus,
)

class ApkInspector(
    private val packageManager: PackageManager,
) {
    fun inspect(apkFile: File): ApkInspection {
        val archiveInfo = packageInfoFromArchive(apkFile)
            ?: throw ApkInspectionException("Android could not parse the downloaded file as a signed APK.")
        val packageName = archiveInfo.packageName.takeIf(String::isNotBlank)
            ?: throw ApkInspectionException("The downloaded APK does not declare a package name.")
        val archiveSigners = signerFingerprints(archiveInfo)
        if (archiveSigners.current.isEmpty()) {
            throw ApkInspectionException("The downloaded APK has no signing certificate information.")
        }
        val installedPackage = installedPackageInfo(packageName)
        val installedSigners = installedPackage?.let(::signerFingerprints)
        val existingInstallStatus = when {
            installedSigners == null -> ExistingInstallStatus.NOT_INSTALLED_OR_NOT_VISIBLE
            installedSigners.current.toSet() == archiveSigners.current.toSet() -> ExistingInstallStatus.SIGNER_MATCH
            else -> ExistingInstallStatus.SIGNER_MISMATCH
        }
        return ApkInspection(
            packageName = packageName,
            versionName = archiveInfo.versionName.orEmpty(),
            versionCode = versionCode(archiveInfo),
            installedVersionName = installedPackage?.versionName,
            installedVersionCode = installedPackage?.let(::versionCode),
            signingCertificateSha256 = archiveSigners.history,
            currentSignerSha256 = archiveSigners.current,
            existingInstallStatus = existingInstallStatus,
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfoFromArchive(apkFile: File): PackageInfo? = packageManager.getPackageArchiveInfo(
        apkFile.absolutePath,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        },
    )

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(packageName: String): PackageInfo? = try {
        packageManager.getPackageInfo(
            packageName,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            },
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun versionCode(packageInfo: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode.toLong()
    }

    @Suppress("DEPRECATION")
    private fun signerFingerprints(packageInfo: PackageInfo): SignerFingerprints {
        val currentSignatures: List<Signature>
        val historicalSignatures: List<Signature>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return SignerFingerprints(emptyList(), emptyList())
            currentSignatures = signingInfo.apkContentsSigners?.toList().orEmpty()
            historicalSignatures = if (signingInfo.hasMultipleSigners()) {
                currentSignatures
            } else {
                signingInfo.signingCertificateHistory?.toList().orEmpty().ifEmpty { currentSignatures }
            }
        } else {
            currentSignatures = packageInfo.signatures?.toList().orEmpty()
            historicalSignatures = currentSignatures
        }
        return SignerFingerprints(
            current = currentSignatures.map(::certificateSha256).distinct().sorted(),
            history = historicalSignatures.map(::certificateSha256).distinct().sorted(),
        )
    }

    private fun certificateSha256(signature: Signature): String = MessageDigest.getInstance("SHA-256")
        .digest(signature.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class SignerFingerprints(
        val current: List<String>,
        val history: List<String>,
    )
}
