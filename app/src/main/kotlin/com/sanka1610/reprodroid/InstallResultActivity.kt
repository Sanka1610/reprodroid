package com.sanka1610.reprodroid

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sanka1610.reprodroid.data.local.InstallAttemptStatus
import kotlinx.coroutines.launch

class InstallResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attemptId = intent.getStringExtra(EXTRA_ATTEMPT_ID) ?: intent.data?.lastPathSegment
        if (attemptId == null) {
            finish()
            return
        }
        val packageInstallerStatus = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val isReleaseAttempt = intent.getStringExtra(EXTRA_ATTEMPT_SOURCE) == SOURCE_RELEASE
        lifecycleScope.launch {
            val app = application as ReproDroidApplication
            suspend fun record(status: InstallAttemptStatus, platformStatus: Int, message: String?) {
                if (isReleaseAttempt) {
                    app.managedAppRepository.recordReleaseInstallStatus(
                        attemptId,
                        status,
                        platformStatus,
                        message,
                    )
                } else {
                    app.jobRepository.recordInstallStatus(attemptId, status, platformStatus, message)
                }
            }
            if (packageInstallerStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                record(InstallAttemptStatus.PENDING_USER_ACTION, packageInstallerStatus, statusMessage)
                val confirmationIntent = pendingUserActionIntent()
                if (confirmationIntent == null) {
                    record(
                        InstallAttemptStatus.FAILED,
                        PackageInstaller.STATUS_FAILURE,
                        "PackageInstaller did not provide its user-confirmation intent.",
                    )
                } else {
                    try {
                        startActivity(confirmationIntent)
                    } catch (failure: Throwable) {
                        record(
                            InstallAttemptStatus.FAILED,
                            PackageInstaller.STATUS_FAILURE,
                            failure.message ?: "The system install confirmation could not be opened.",
                        )
                    }
                }
            } else {
                record(
                    when (packageInstallerStatus) {
                        PackageInstaller.STATUS_SUCCESS -> InstallAttemptStatus.SUCCEEDED
                        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallAttemptStatus.CANCELLED
                        else -> InstallAttemptStatus.FAILED
                    },
                    packageInstallerStatus,
                    statusMessage,
                )
            }
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun pendingUserActionIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        intent.getParcelableExtra(Intent.EXTRA_INTENT)
    }

    companion object {
        const val EXTRA_ATTEMPT_ID = "com.sanka1610.reprodroid.extra.INSTALL_ATTEMPT_ID"
        const val EXTRA_ATTEMPT_SOURCE = "com.sanka1610.reprodroid.extra.INSTALL_ATTEMPT_SOURCE"
        const val SOURCE_RELEASE = "RELEASE"
    }
}
