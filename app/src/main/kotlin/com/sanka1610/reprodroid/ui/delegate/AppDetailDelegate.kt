package com.sanka1610.reprodroid.ui.delegate

import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.*
import com.sanka1610.reprodroid.data.repository.BuildConfigurationInput
import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import com.sanka1610.reprodroid.ui.state.*
import com.sanka1610.reprodroid.work.ReleaseCheckScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class AppDetailDelegate(
    private val application: ReproDroidApplication,
    private val scope: CoroutineScope,
    private val actions: AppActionDelegate,
    private val events: ManagedUiEventStore,
) {
    private val repository = application.managedAppRepository
    private val jobRepository = application.jobRepository
    private val releaseRepository = application.releaseCheckRepository
    private val coreState = combine(
        jobRepository.observeBuildEnvironmentManifests(),
        jobRepository.observeJobs(),
        jobRepository.buildManifestWarnings,
        jobRepository.sourceScanWarnings,
        jobRepository.sandboxWarnings,
    ) { manifests, jobs, buildWarnings, scanWarnings, sandboxWarnings ->
        AppDetailUiState(manifests, jobs, buildWarnings, scanWarnings, sandboxWarnings)
    }
    val state = combine(
        coreState,
        repository.observeAvailability(),
        events.observe(ManagedUiOwner.APP_DETAIL),
    ) { base, availability, event ->
        base.copy(availability = availability, message = event.message, results = event.results)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState())

    fun updatePreferences(registeredAppId: String, update: AppSettingsUpdate, originRoute: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.updatePreferences(registeredAppId, update)
        }, { publishSaved(ManagedUiResultKind.PREFERENCES_SAVED, registeredAppId, originRoute) })

    fun updateMetadata(registeredAppId: String, update: AppMetadataUpdate, originRoute: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.updateMetadata(registeredAppId, update)
        }, { publishSaved(ManagedUiResultKind.METADATA_SAVED, registeredAppId, originRoute) })

    fun stopTracking(registeredAppId: String, originRoute: String) =
        trackingAction(ManagedUiResultKind.TRACKING_STOPPED, registeredAppId, originRoute) {
            repository.stopTracking(registeredAppId)
        }

    fun stopTrackingAfterConfirmedUninstall(registeredAppId: String, originRoute: String) =
        trackingAction(ManagedUiResultKind.TRACKING_STOPPED_AFTER_UNINSTALL, registeredAppId, originRoute) {
            repository.stopTrackingAfterConfirmedUninstall(registeredAppId)
        }

    fun confirmUninstall(registeredAppId: String, originRoute: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.confirmUninstall(registeredAppId)
        }, {
            publishRemovalResult(
                ManagedUiResultKind.UNINSTALL_CONFIRMED,
                registeredAppId,
                originRoute,
                ReproDroidRoute.AppInformation(registeredAppId),
            )
        })

    fun resumeTracking(registeredAppId: String) = actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
        repository.resumeTracking(registeredAppId)
        ReleaseCheckScheduler.reconcile(application, releaseRepository, forceRecalculate = true)
    })

    fun saveBuildConfiguration(registeredAppId: String, expectedRevision: Long?, input: BuildConfigurationInput) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
            repository.saveBuildConfiguration(registeredAppId, expectedRevision, input)
        })

    fun startComparison(registeredAppId: String) = actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
        repository.startComparison(registeredAppId)
    })

    fun refreshComparison(registeredAppId: String, comparisonRunId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.refreshComparison(comparisonRunId) })

    fun confirmComparison(registeredAppId: String, comparisonRunId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.confirmComparison(comparisonRunId) })

    fun continueComparisonSourceScan(registeredAppId: String, comparisonRunId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.continueComparisonSourceScan(comparisonRunId) })

    fun refreshInstalledState(registeredAppId: String) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.refreshInstalledStateForApp(registeredAppId) })

    fun install(registeredAppId: String, riskConfirmed: Boolean) =
        actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, { repository.installManagedApp(registeredAppId, riskConfirmed) })

    private fun trackingAction(
        kind: ManagedUiResultKind,
        registeredAppId: String,
        originRoute: String,
        action: suspend () -> Unit,
    ) = actions.run(ManagedUiOwner.APP_DETAIL, registeredAppId, {
        action()
        ReleaseCheckScheduler.reconcile(application, releaseRepository, forceRecalculate = true)
    }, { publishRemovalResult(kind, registeredAppId, originRoute, ReproDroidRoute.Apps) })

    private fun publishSaved(kind: ManagedUiResultKind, registeredAppId: String, originRoute: String) {
        events.publishResult(
            ManagedUiResult(
                owner = ManagedUiOwner.APP_DETAIL,
                kind = kind,
                originRoute = originRoute,
                destination = ReproDroidRoute.AppInformation(registeredAppId),
                registeredAppId = registeredAppId,
            ),
        )
    }

    private fun publishRemovalResult(
        kind: ManagedUiResultKind,
        registeredAppId: String,
        originRoute: String,
        destination: ReproDroidRoute,
    ) {
        events.publishResult(
            ManagedUiResult(
                owner = ManagedUiOwner.APP_DETAIL,
                kind = kind,
                originRoute = originRoute,
                destination = destination,
                registeredAppId = registeredAppId,
                requiresRemovalTarget = true,
                clearRemovalTarget = true,
            ),
        )
    }
}
