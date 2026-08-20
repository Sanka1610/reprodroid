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
        lifecycleScope.launch {
            val repository = (application as ReproDroidApplication).jobRepository
            if (packageInstallerStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                repository.recordInstallStatus(
                    attemptId = attemptId,
                    status = InstallAttemptStatus.PENDING_USER_ACTION,
                    packageInstallerStatus = packageInstallerStatus,
                    statusMessage = statusMessage,
                )
                val confirmationIntent = pendingUserActionIntent()
                if (confirmationIntent == null) {
                    repository.recordInstallStatus(
                        attemptId = attemptId,
                        status = InstallAttemptStatus.FAILED,
                        packageInstallerStatus = PackageInstaller.STATUS_FAILURE,
                        statusMessage = "PackageInstaller did not provide its user-confirmation intent.",
                    )
                } else {
                    try {
                        startActivity(confirmationIntent)
                    } catch (failure: Throwable) {
                        repository.recordInstallStatus(
                            attemptId = attemptId,
                            status = InstallAttemptStatus.FAILED,
                            packageInstallerStatus = PackageInstaller.STATUS_FAILURE,
                            statusMessage = failure.message ?: "The system install confirmation could not be opened.",
                        )
                    }
                }
            } else {
                repository.recordInstallStatus(
                    attemptId = attemptId,
                    status = when (packageInstallerStatus) {
                        PackageInstaller.STATUS_SUCCESS -> InstallAttemptStatus.SUCCEEDED
                        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallAttemptStatus.CANCELLED
                        else -> InstallAttemptStatus.FAILED
                    },
                    packageInstallerStatus = packageInstallerStatus,
                    statusMessage = statusMessage,
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
    }
}
