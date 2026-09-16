package com.sanka1610.reprodroid.data.artifact

import android.content.Context
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageInstallerSession
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstallerHidden
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import dev.rikka.tools.refine.Refine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

enum class ShizukuPermissionState {
    GRANTED,
    SERVICE_UNAVAILABLE,
    UNSUPPORTED,
    DENIED,
}

data class PrivilegedInstallSession(
    val sessionId: Int,
    val session: PackageInstaller.Session,
    val installerPackageName: String,
)

/**
 * Narrow Shizuku/Sui bridge for PackageInstaller sessions. APK policy and byte validation remain
 * in ReproDroid; this class only changes the identity used to create and commit a session.
 * The binder-wrapping approach is adapted from the MIT-licensed shizuku_apk_installer project;
 * its notice is included in the offline third-party license view.
 */
class ShizukuPackageInstaller(private val context: Context) {
    fun permissionState(): ShizukuPermissionState = runCatching {
        when {
            !Shizuku.pingBinder() -> ShizukuPermissionState.SERVICE_UNAVAILABLE
            Shizuku.isPreV11() -> ShizukuPermissionState.UNSUPPORTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuPermissionState.GRANTED
            else -> ShizukuPermissionState.DENIED
        }
    }.getOrDefault(ShizukuPermissionState.SERVICE_UNAVAILABLE)

    suspend fun requestPermission(): ShizukuPermissionState = withContext(Dispatchers.Main.immediate) {
        when (val current = permissionState()) {
            ShizukuPermissionState.GRANTED,
            ShizukuPermissionState.SERVICE_UNAVAILABLE,
            ShizukuPermissionState.UNSUPPORTED,
            -> current
            ShizukuPermissionState.DENIED -> {
                if (Shizuku.shouldShowRequestPermissionRationale()) return@withContext current
                withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MILLIS) {
                    suspendCancellableCoroutine { continuation ->
                        val requestCode = nextRequestCode.incrementAndGet()
                        lateinit var listener: Shizuku.OnRequestPermissionResultListener
                        listener = Shizuku.OnRequestPermissionResultListener { resultCode, grantResult ->
                            if (resultCode != requestCode || !continuation.isActive) {
                                return@OnRequestPermissionResultListener
                            }
                            Shizuku.removeRequestPermissionResultListener(listener)
                            continuation.resume(
                                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                    ShizukuPermissionState.GRANTED
                                } else {
                                    ShizukuPermissionState.DENIED
                                },
                            )
                        }
                        Shizuku.addRequestPermissionResultListener(listener)
                        continuation.invokeOnCancellation {
                            Shizuku.removeRequestPermissionResultListener(listener)
                        }
                        runCatching { Shizuku.requestPermission(requestCode) }
                            .onFailure {
                                Shizuku.removeRequestPermissionResultListener(listener)
                                if (continuation.isActive) {
                                    continuation.resume(ShizukuPermissionState.SERVICE_UNAVAILABLE)
                                }
                            }
                    }
                } ?: ShizukuPermissionState.SERVICE_UNAVAILABLE
            }
        }
    }

    fun createSession(
        params: PackageInstaller.SessionParams,
        recordGooglePlayAsInstaller: Boolean,
    ): PrivilegedInstallSession {
        check(permissionState() == ShizukuPermissionState.GRANTED) {
            "Shizuku or Sui is not running with permission for ReproDroid."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content", "Landroid/os")
        }
        val installerPackageName = if (recordGooglePlayAsInstaller) GOOGLE_PLAY_PACKAGE else SHELL_PACKAGE
        val packageInstallerBinder = privilegedPackageInstallerBinder()
        val packageInstaller = packageInstaller(packageInstallerBinder, installerPackageName)
        val sessionId = packageInstaller.createSession(params)
        val sessionBinder = IPackageInstallerSession.Stub.asInterface(
            ShizukuBinderWrapper(packageInstallerBinder.openSession(sessionId).asBinder()),
        )
        val session = Refine.unsafeCast<PackageInstaller.Session>(PackageInstallerHidden.SessionHidden(sessionBinder))
        return PrivilegedInstallSession(sessionId, session, installerPackageName)
    }

    private fun privilegedPackageInstallerBinder(): IPackageInstaller {
        val packageManager = IPackageManager.Stub.asInterface(
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")),
        )
        return IPackageInstaller.Stub.asInterface(
            ShizukuBinderWrapper(packageManager.packageInstaller.asBinder()),
        )
    }

    private fun packageInstaller(
        binder: IPackageInstaller,
        installerPackageName: String,
    ): PackageInstaller {
        val shellContext = context.createPackageContext(SHELL_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        val userId = Process.myUid() / PER_USER_RANGE
        val hidden = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PackageInstallerHidden(binder, installerPackageName, shellContext.attributionTag, userId)
        } else {
            PackageInstallerHidden(binder, installerPackageName, userId)
        }
        return Refine.unsafeCast(hidden)
    }

    companion object {
        const val GOOGLE_PLAY_PACKAGE = "com.android.vending"
        const val SHELL_PACKAGE = "com.android.shell"
        private const val PER_USER_RANGE = 100_000
        private const val PERMISSION_REQUEST_TIMEOUT_MILLIS = 30_000L
        private val nextRequestCode = AtomicInteger(7_000)
    }
}
