package com.sanka1610.reprodroid.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed interface ReproDroidRoute {
    data object Apps : ReproDroidRoute
    data object InactiveApps : ReproDroidRoute
    data object AddSource : ReproDroidRoute
    data object AddAnalysis : ReproDroidRoute
    data object AddOptions : ReproDroidRoute
    data object AddConfirm : ReproDroidRoute
    data object Settings : ReproDroidRoute
    data object DataManagement : ReproDroidRoute
    data object DataStorage : ReproDroidRoute
    data object DataInactive : ReproDroidRoute
    data object RunnerSettings : ReproDroidRoute
    data object RunnerStorage : ReproDroidRoute
    data object Toolchains : ReproDroidRoute
    data object Jobs : ReproDroidRoute
    data object UpdateSettings : ReproDroidRoute
    data object Authentication : ReproDroidRoute
    data object Backup : ReproDroidRoute
    data object GitHubStarsImport : ReproDroidRoute
    data class AppInformation(val registeredAppId: String) : ReproDroidRoute
    data class AppEdit(val registeredAppId: String) : ReproDroidRoute
    data class AppSettings(val registeredAppId: String) : ReproDroidRoute
    data class AppTechnical(val registeredAppId: String) : ReproDroidRoute
    data class Comparison(val comparisonRunId: String) : ReproDroidRoute

    val isRoot: Boolean
        get() = this == Apps || this == AddSource || this == Settings

    val isAddFlow: Boolean
        get() = when (this) {
            AddSource,
            AddAnalysis,
            AddOptions,
            AddConfirm,
            -> true
            else -> false
        }

    val appId: String?
        get() = when (this) {
            is AppInformation -> registeredAppId
            is AppEdit -> registeredAppId
            is AppSettings -> registeredAppId
            is AppTechnical -> registeredAppId
            else -> null
        }

    fun encode(): String = when (this) {
        Apps -> "apps"
        InactiveApps -> "apps/inactive"
        AddSource -> "add/source"
        AddAnalysis -> "add/analysis"
        AddOptions -> "add/options"
        AddConfirm -> "add/confirm"
        Settings -> "settings"
        DataManagement -> "settings/data"
        DataStorage -> "settings/data/storage"
        DataInactive -> "settings/data/inactive"
        RunnerSettings -> "settings/runner"
        RunnerStorage -> "settings/runner/storage"
        Toolchains -> "settings/toolchains"
        Jobs -> "settings/jobs"
        UpdateSettings -> "settings/updates"
        Authentication -> "settings/authentication"
        Backup -> "settings/backup"
        GitHubStarsImport -> "import/github-stars"
        is AppInformation -> "apps/${registeredAppId.segment()}/information"
        is AppEdit -> "apps/${registeredAppId.segment()}/edit"
        is AppSettings -> "apps/${registeredAppId.segment()}/settings"
        is AppTechnical -> "apps/${registeredAppId.segment()}/technical"
        is Comparison -> "comparisons/${comparisonRunId.segment()}"
    }

    companion object {
        fun parse(value: String?): ReproDroidRoute {
            val route = value?.trim()?.trim('/') ?: return Apps
            return when (route) {
                "apps" -> Apps
                "apps/inactive" -> InactiveApps
                "add/source" -> AddSource
                "add/analysis" -> AddAnalysis
                "add/options" -> AddOptions
                "add/confirm" -> AddConfirm
                "settings" -> Settings
                "settings/data" -> DataManagement
                "settings/data/storage" -> DataStorage
                "settings/data/inactive" -> DataInactive
                "settings/runner" -> RunnerSettings
                "settings/runner/storage" -> RunnerStorage
                "settings/toolchains" -> Toolchains
                "settings/jobs" -> Jobs
                "settings/updates" -> UpdateSettings
                "settings/authentication" -> Authentication
                "settings/backup" -> Backup
                "import/github-stars" -> GitHubStarsImport
                else -> parseAppRoute(route) ?: parseComparisonRoute(route) ?: Apps
            }
        }

        fun appInformation(registeredAppId: String): ReproDroidRoute {
            val appId = canonicalUuid(registeredAppId) ?: return Apps
            return AppInformation(appId)
        }

        private fun parseAppRoute(route: String): ReproDroidRoute? {
            val segments = route.split('/')
            if (segments.size != 3 || segments[0] != "apps") return null
            val appId = canonicalUuid(segments[1].unsegment()) ?: return null
            return when (segments[2]) {
                "information" -> AppInformation(appId)
                "edit" -> AppEdit(appId)
                "settings" -> AppSettings(appId)
                "technical" -> AppTechnical(appId)
                else -> null
            }
        }

        private fun parseComparisonRoute(route: String): ReproDroidRoute? {
            val segments = route.split('/')
            if (segments.size != 2 || segments[0] != "comparisons") return null
            return canonicalUuid(segments[1].unsegment())?.let(::Comparison)
        }

        private fun canonicalUuid(value: String): String? = runCatching {
            UUID.fromString(value).toString().takeIf { it == value }
        }.getOrNull()

        val addFlowRoutes: Set<ReproDroidRoute>
            get() = setOf(AddSource, AddAnalysis, AddOptions, AddConfirm)
    }
}

private fun String.segment(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
private fun String.unsegment(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
