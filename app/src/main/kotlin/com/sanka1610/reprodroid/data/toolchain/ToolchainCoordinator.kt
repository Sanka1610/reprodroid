package com.sanka1610.reprodroid.data.toolchain

import com.sanka1610.reprodroid.data.local.ReproDroidDatabase
import com.sanka1610.reprodroid.data.local.ToolchainInstallationReferenceEntity
import com.sanka1610.reprodroid.data.network.CreateToolchainInstallationRequest
import com.sanka1610.reprodroid.data.network.ResolveToolchainPlanRequest
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.network.ToolchainComponent
import com.sanka1610.reprodroid.data.network.ToolchainInstallationResponse
import com.sanka1610.reprodroid.data.network.ToolchainInstallationState
import com.sanka1610.reprodroid.data.network.ToolchainInventoryResponse
import com.sanka1610.reprodroid.data.network.ToolchainLicenseAcceptanceRequest
import com.sanka1610.reprodroid.data.network.ToolchainPlanResponse
import com.sanka1610.reprodroid.data.network.ToolchainRemovalPreviewResponse
import com.sanka1610.reprodroid.data.network.ToolchainRequirement
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.UUID

data class ToolchainUiState(
    val plan: ToolchainPlanResponse? = null,
    val inventory: ToolchainInventoryResponse? = null,
    val installation: ToolchainInstallationResponse? = null,
    val removalPreview: ToolchainRemovalPreviewResponse? = null,
    val busy: Boolean = false,
)

class ToolchainCoordinator(
    database: ReproDroidDatabase,
    private val runnerApi: RunnerApiClient,
) {
    private val dao = database.toolchainDao()
    private val _state = MutableStateFlow(ToolchainUiState())
    val state = _state.asStateFlow()

    suspend fun refresh() {
        _state.value = _state.value.copy(busy = true)
        try {
            val plan = runnerApi.resolveToolchainPlan(ResolveToolchainPlanRequest(BASELINE_REQUIREMENTS))
            val inventory = runnerApi.getToolchainInventory()
            _state.value = _state.value.copy(plan = plan, inventory = inventory, removalPreview = null)
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    suspend fun recoverActive() {
        dao.activeInstallationReferences().forEach { reference ->
            val installation = runnerApi.getToolchainInstallation(reference.installationId)
            persist(installation, reference.registeredAppId, reference.buildSettingsRevision)
            _state.value = _state.value.copy(installation = installation)
        }
    }

    suspend fun install(acceptedLicenseIds: Set<String>) {
        val plan = requireNotNull(_state.value.plan) { "Resolve a current toolchain plan first." }
        val expected = plan.requiredLicenses.map { it.licenseId }.toSet()
        require(acceptedLicenseIds == expected) { "Accept every current toolchain license before installation." }
        _state.value = _state.value.copy(busy = true)
        try {
            var installation = runnerApi.createToolchainInstallation(
                CreateToolchainInstallationRequest(
                    plan.planSha256,
                    plan.catalogSha256,
                    BASELINE_REQUIREMENTS,
                    plan.requiredLicenses.map { ToolchainLicenseAcceptanceRequest(it.licenseId, it.textSha256, true) },
                ),
                UUID.randomUUID().toString(),
            )
            persist(installation)
            _state.value = _state.value.copy(installation = installation)
            while (installation.state !in TERMINAL_STATES) {
                delay(750)
                installation = runnerApi.getToolchainInstallation(installation.installationId)
                persist(installation)
                _state.value = _state.value.copy(installation = installation)
            }
            _state.value = _state.value.copy(inventory = runnerApi.getToolchainInventory())
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    suspend fun cancel() {
        val installation = requireNotNull(_state.value.installation) { "No installation is selected." }
        val updated = runnerApi.cancelToolchainInstallation(installation.installationId, UUID.randomUUID().toString())
        persist(updated)
        _state.value = _state.value.copy(installation = updated)
    }

    suspend fun previewRemoval(artifactIds: Set<String>) {
        require(artifactIds.isNotEmpty()) { "Select installed toolchains to remove." }
        _state.value = _state.value.copy(busy = true)
        try {
            val preview = runnerApi.previewToolchainRemoval(artifactIds.sorted(), UUID.randomUUID().toString())
            _state.value = _state.value.copy(removalPreview = preview)
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    suspend fun executeRemoval() {
        val preview = requireNotNull(_state.value.removalPreview) { "Preview toolchain removal first." }
        _state.value = _state.value.copy(busy = true)
        try {
            runnerApi.executeToolchainRemoval(preview.previewId, UUID.randomUUID().toString())
            _state.value = _state.value.copy(
                inventory = runnerApi.getToolchainInventory(),
                removalPreview = null,
            )
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    private suspend fun persist(
        installation: ToolchainInstallationResponse,
        registeredAppId: String? = null,
        buildSettingsRevision: Long? = null,
    ) {
        dao.upsertInstallationReference(
            ToolchainInstallationReferenceEntity(
                runnerId = installation.runnerId,
                installationId = installation.installationId,
                operationId = installation.operationId,
                registeredAppId = registeredAppId,
                buildSettingsRevision = buildSettingsRevision,
                planSha256 = installation.planSha256,
                catalogSha256 = installation.catalogSha256,
                state = installation.state.name,
                observedAt = Instant.now().toString(),
            ),
        )
    }

    companion object {
        val BASELINE_REQUIREMENTS = listOf(
            ToolchainRequirement(ToolchainComponent.JDK, "21.0.12+1"),
            ToolchainRequirement(ToolchainComponent.GRADLE, "8.14.3"),
            ToolchainRequirement(ToolchainComponent.GRADLE, "9.6.1"),
            ToolchainRequirement(ToolchainComponent.GRADLE, "9.7.1"),
            ToolchainRequirement(ToolchainComponent.ANDROID_COMMAND_LINE_TOOLS, "15859902"),
            ToolchainRequirement(ToolchainComponent.ANDROID_PLATFORM, "36-r02"),
            ToolchainRequirement(ToolchainComponent.ANDROID_PLATFORM, "37.0-r02"),
            ToolchainRequirement(ToolchainComponent.ANDROID_BUILD_TOOLS, "36.0.0"),
            ToolchainRequirement(ToolchainComponent.ANDROID_BUILD_TOOLS, "37.0.0"),
        )
        private val TERMINAL_STATES = setOf(
            ToolchainInstallationState.INSTALLED,
            ToolchainInstallationState.CANCELLED,
            ToolchainInstallationState.FAILED,
            ToolchainInstallationState.RECONCILIATION_REQUIRED,
        )
    }
}
