package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckBatteryPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseCheckNetworkPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckScheduleMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Serializable
data class EffectiveReleaseCheckSettings(
    val globalRevision: Long,
    val appRevision: Long?,
    val enabled: Boolean,
    val scheduleMode: String,
    val intervalHours: Int,
    val dailyLocalMinute: Int,
    val releaseChannel: String,
    val networkPolicy: String,
    val batteryPolicy: String,
    val notificationMuted: Boolean,
)

data class ReleaseCheckDeviceState(
    val networkAvailable: Boolean,
    val networkMetered: Boolean,
    val batteryPercent: Int?,
)

object ReleaseCheckPolicy {
    fun validate(settings: ReleaseCheckSettingsEntity): ReleaseCheckSettingsEntity {
        require(settings.singletonId == ReleaseCheckSettingsEntity.SINGLETON_ID) { "Invalid settings identity." }
        enumValue<ReleaseCheckScheduleMode>(settings.scheduleMode)
        enumValue<ReleaseCheckChannel>(settings.releaseChannel)
        enumValue<ReleaseCheckNetworkPolicy>(settings.networkPolicy)
        enumValue<ReleaseCheckBatteryPolicy>(settings.batteryPolicy)
        require(settings.intervalHours in 1..24) { "Check interval must be between 1 and 24 hours." }
        require(settings.dailyLocalMinute in 0..1439) { "Daily check time must be a local minute from 0 to 1439." }
        require(settings.revision > 0) { "Settings revision must be positive." }
        require(runCatching { Instant.parse(settings.updatedAt) }.isSuccess) { "Settings timestamp is invalid." }
        return settings
    }

    fun effective(
        global: ReleaseCheckSettingsEntity,
        override: AppReleaseCheckOverrideEntity?,
    ): EffectiveReleaseCheckSettings {
        validate(global)
        override?.let(::validateOverride)
        val enabled = global.enabled && (override?.enabled ?: true)
        return EffectiveReleaseCheckSettings(
            globalRevision = global.revision,
            appRevision = override?.revision,
            enabled = enabled,
            scheduleMode = override?.scheduleMode ?: global.scheduleMode,
            intervalHours = override?.intervalHours ?: global.intervalHours,
            dailyLocalMinute = override?.dailyLocalMinute ?: global.dailyLocalMinute,
            releaseChannel = override?.releaseChannel ?: global.releaseChannel,
            networkPolicy = override?.networkPolicy ?: global.networkPolicy,
            batteryPolicy = override?.batteryPolicy ?: global.batteryPolicy,
            notificationMuted = override?.notificationMuted ?: false,
        ).also(::validateEffective)
    }

    fun nextTerminalTime(
        effective: EffectiveReleaseCheckSettings,
        terminalAt: Instant,
        zoneId: ZoneId,
    ): Instant = when (enumValue<ReleaseCheckScheduleMode>(effective.scheduleMode)) {
        ReleaseCheckScheduleMode.INTERVAL -> terminalAt.plus(effective.intervalHours.toLong(), ChronoUnit.HOURS)
        ReleaseCheckScheduleMode.DAILY_LOCAL_TIME -> {
            val local = ZonedDateTime.ofInstant(terminalAt, zoneId)
            val hour = effective.dailyLocalMinute / 60
            val minute = effective.dailyLocalMinute % 60
            var candidate = local.toLocalDate().atTime(hour, minute).atZone(zoneId)
            if (!candidate.toInstant().isAfter(terminalAt)) candidate = candidate.plusDays(1)
            candidate.toInstant()
        }
    }

    fun deferReason(effective: EffectiveReleaseCheckSettings, state: ReleaseCheckDeviceState): String? {
        val network = enumValue<ReleaseCheckNetworkPolicy>(effective.networkPolicy)
        if (!state.networkAvailable || (network == ReleaseCheckNetworkPolicy.UNMETERED_ONLY && state.networkMetered)) {
            return "DEFERRED_NETWORK"
        }
        val battery = enumValue<ReleaseCheckBatteryPolicy>(effective.batteryPolicy)
        if (battery == ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT && (state.batteryPercent == null || state.batteryPercent <= 20)) {
            return "DEFERRED_BATTERY"
        }
        return null
    }

    private fun validateOverride(override: AppReleaseCheckOverrideEntity) {
        override.scheduleMode?.let { enumValue<ReleaseCheckScheduleMode>(it) }
        override.releaseChannel?.let { enumValue<ReleaseCheckChannel>(it) }
        override.networkPolicy?.let { enumValue<ReleaseCheckNetworkPolicy>(it) }
        override.batteryPolicy?.let { enumValue<ReleaseCheckBatteryPolicy>(it) }
        override.intervalHours?.let { require(it in 1..24) { "App check interval must be between 1 and 24 hours." } }
        override.dailyLocalMinute?.let { require(it in 0..1439) { "App daily check time is invalid." } }
        require(override.revision > 0) { "App settings revision must be positive." }
        require(runCatching { Instant.parse(override.updatedAt) }.isSuccess) { "App settings timestamp is invalid." }
    }

    private fun validateEffective(settings: EffectiveReleaseCheckSettings) {
        enumValue<ReleaseCheckScheduleMode>(settings.scheduleMode)
        enumValue<ReleaseCheckChannel>(settings.releaseChannel)
        enumValue<ReleaseCheckNetworkPolicy>(settings.networkPolicy)
        enumValue<ReleaseCheckBatteryPolicy>(settings.batteryPolicy)
        require(settings.intervalHours in 1..24)
        require(settings.dailyLocalMinute in 0..1439)
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw IllegalArgumentException("Unknown ${T::class.simpleName}: $value")
}
