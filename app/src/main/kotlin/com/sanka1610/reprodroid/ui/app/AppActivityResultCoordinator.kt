package com.sanka1610.reprodroid.ui.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.sanka1610.reprodroid.data.log.AppLogExportManager
import com.sanka1610.reprodroid.ui.shared.AUDIT_MIME_TYPE

internal data class UninstallActivityTarget(
    val registeredAppId: String,
    val packageName: String,
    val stopTracking: Boolean,
)

internal class AppActivityResultCoordinator(
    private val launchUninstallIntent: (UninstallActivityTarget) -> Unit,
    private val launchNotificationPermission: () -> Unit,
    private val launchLogDestination: () -> Unit,
    private val launchAuditDestination: (String) -> Unit,
) {
    fun uninstall(target: UninstallActivityTarget) = launchUninstallIntent(target)

    fun requestNotificationPermission() = launchNotificationPermission()

    fun createLogExportDocument() = launchLogDestination()

    fun createAuditExportDocument(suggestedName: String) = launchAuditDestination(suggestedName)
}

@Composable
internal fun rememberAppActivityResultCoordinator(
    onUninstallResult: (UninstallActivityTarget) -> Unit,
    onLogDestination: (Uri) -> Unit,
    onAuditDestination: (Uri) -> Unit,
): AppActivityResultCoordinator {
    val currentUninstallResult by rememberUpdatedState(onUninstallResult)
    val currentLogDestination by rememberUpdatedState(onLogDestination)
    val currentAuditDestination by rememberUpdatedState(onAuditDestination)
    var uninstallAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var uninstallPackageName by rememberSaveable { mutableStateOf<String?>(null) }
    var uninstallStopsTracking by rememberSaveable { mutableStateOf(false) }

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val appId = uninstallAppId
        val packageName = uninstallPackageName
        val stopTracking = uninstallStopsTracking
        uninstallAppId = null
        uninstallPackageName = null
        uninstallStopsTracking = false
        if (appId != null && packageName != null) {
            currentUninstallResult(
                UninstallActivityTarget(
                    registeredAppId = appId,
                    packageName = packageName,
                    stopTracking = stopTracking,
                ),
            )
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val logDestinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AppLogExportManager.MIME_TYPE),
    ) { destination -> destination?.let(currentLogDestination) }
    val auditDestinationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AUDIT_MIME_TYPE),
    ) { destination -> destination?.let(currentAuditDestination) }

    return remember(
        uninstallLauncher,
        notificationPermissionLauncher,
        logDestinationLauncher,
        auditDestinationLauncher,
    ) {
        AppActivityResultCoordinator(
            launchUninstallIntent = { target ->
                uninstallAppId = target.registeredAppId
                uninstallPackageName = target.packageName
                uninstallStopsTracking = target.stopTracking
                uninstallLauncher.launch(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${target.packageName}"))
                        .putExtra(Intent.EXTRA_RETURN_RESULT, true),
                )
            },
            launchNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            launchLogDestination = {
                logDestinationLauncher.launch(AppLogExportManager.SUGGESTED_FILE_NAME)
            },
            launchAuditDestination = auditDestinationLauncher::launch,
        )
    }
}
