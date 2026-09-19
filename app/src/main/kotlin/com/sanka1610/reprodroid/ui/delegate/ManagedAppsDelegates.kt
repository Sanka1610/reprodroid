package com.sanka1610.reprodroid.ui.delegate

import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ManagedAppsDelegates(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
) {
    val events = ManagedUiEventStore()
    private val appActions = AppActionDelegate(scope, application.appLogStore, events)

    val apps = AppsDelegate(application, scope, appActions, events)
    val registration = RegistrationDelegate(application, scope, appActions, events)
    val appDetail = AppDetailDelegate(application, scope, appActions, events)
    val release = ReleaseDelegate(application, scope, appActions, events)
    val providerAuth = ProviderAuthDelegate(application, scope, events)
    val storage = StorageDelegate(application, scope, events)
    val runner = RunnerDelegate(application, scope, events)
    val toolchain = ToolchainDelegate(application, scope, events)
    val deletionExport = DeletionExportDelegate(application, scope, appActions, storage, events)

    fun initialize() {
        scope.launch {
            try {
                apps.initialize()
                storage.initialize()
                toolchain.initialize()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                events.publishMessage(ManagedUiOwner.APPS, failure.userMessage())
            }
        }
    }

    fun updateGlobalSettings(settings: GlobalSettingsEntity) {
        apps.updateGlobalSettings(settings) {
            storage.refreshLocalSummary()
            registration.clearPreview()
        }
    }
}
