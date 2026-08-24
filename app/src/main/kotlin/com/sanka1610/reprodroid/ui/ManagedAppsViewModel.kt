package com.sanka1610.reprodroid.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sanka1610.reprodroid.ReproDroidApplication
import com.sanka1610.reprodroid.data.local.ManagementMode
import com.sanka1610.reprodroid.data.local.PreferredAbi
import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import com.sanka1610.reprodroid.data.provider.ResolvedGitHubRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReleasePreviewState(
    val release: ResolvedGitHubRelease? = null,
    val isLoading: Boolean = false,
)

class ManagedAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ReproDroidApplication).managedAppRepository

    val apps = repository.observeApps().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _preview = MutableStateFlow(ReleasePreviewState())
    val preview = _preview.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _activeAppIds = MutableStateFlow<Set<String>>(emptySet())
    val activeAppIds = _activeAppIds.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.recoverInterruptedDownloads() }
                .onFailure { _message.value = it.userMessage() }
        }
    }

    fun preview(repositoryUrl: String) {
        viewModelScope.launch {
            _preview.value = ReleasePreviewState(isLoading = true)
            try {
                _preview.value = ReleasePreviewState(release = repository.previewLatest(repositoryUrl.trim()))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _preview.value = ReleasePreviewState()
                _message.value = failure.userMessage()
            }
        }
    }

    fun register(mode: ManagementMode, onRegistered: (String) -> Unit) {
        val resolved = _preview.value.release ?: return
        viewModelScope.launch {
            _preview.value = _preview.value.copy(isLoading = true)
            try {
                val appId = repository.registerAndDownload(resolved, mode)
                _preview.value = ReleasePreviewState()
                onRegistered(appId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
                _preview.value = _preview.value.copy(isLoading = false)
            }
        }
    }

    fun refresh(registeredAppId: String) {
        if (registeredAppId in _activeAppIds.value) return
        viewModelScope.launch {
            _activeAppIds.value += registeredAppId
            try {
                repository.refresh(registeredAppId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            } finally {
                _activeAppIds.value -= registeredAppId
            }
        }
    }

    fun updatePreferences(
        registeredAppId: String,
        releaseVariant: ReleaseVariantPreference,
        preferredAbi: PreferredAbi,
        onSaved: () -> Unit,
    ) {
        if (registeredAppId in _activeAppIds.value) return
        viewModelScope.launch {
            _activeAppIds.value += registeredAppId
            try {
                repository.updatePreferences(registeredAppId, releaseVariant, preferredAbi)
                onSaved()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            } finally {
                _activeAppIds.value -= registeredAppId
            }
        }
    }

    fun startComparison(registeredAppId: String) = runAppAction(registeredAppId) {
        repository.startComparison(registeredAppId)
    }

    fun refreshComparison(registeredAppId: String, comparisonRunId: String) =
        runAppAction(registeredAppId) { repository.refreshComparison(comparisonRunId) }

    fun confirmComparison(registeredAppId: String, comparisonRunId: String) =
        runAppAction(registeredAppId) { repository.confirmComparison(comparisonRunId) }

    fun clearPreview() { _preview.value = ReleasePreviewState() }
    fun clearMessage() { _message.value = null }

    private fun runAppAction(registeredAppId: String, action: suspend () -> Unit) {
        if (registeredAppId in _activeAppIds.value) return
        viewModelScope.launch {
            _activeAppIds.value += registeredAppId
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                _message.value = failure.userMessage()
            } finally {
                _activeAppIds.value -= registeredAppId
            }
        }
    }

    private fun Throwable.userMessage(): String = message ?: "ReproDroid operation failed."
}
