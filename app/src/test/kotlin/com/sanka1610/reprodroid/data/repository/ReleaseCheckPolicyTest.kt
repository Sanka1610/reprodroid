package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.AppReleaseCheckOverrideEntity
import com.sanka1610.reprodroid.data.local.ReleaseCheckBatteryPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckChannel
import com.sanka1610.reprodroid.data.local.ReleaseCheckNetworkPolicy
import com.sanka1610.reprodroid.data.local.ReleaseCheckScheduleMode
import com.sanka1610.reprodroid.data.local.ReleaseCheckSettingsEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCheckPolicyTest {
    @Test
    fun `global and app enabled flags are conjunctive while nullable app values override global values`() {
        val global = settings(
            enabled = true,
            scheduleMode = ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name,
            intervalHours = 12,
            dailyLocalMinute = 8 * 60,
            releaseChannel = ReleaseCheckChannel.INCLUDE_PRERELEASE.name,
            networkPolicy = ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name,
            batteryPolicy = ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name,
        )
        val override = AppReleaseCheckOverrideEntity(
            registeredAppId = "app",
            enabled = false,
            scheduleMode = ReleaseCheckScheduleMode.INTERVAL.name,
            intervalHours = 3,
            releaseChannel = ReleaseCheckChannel.STABLE_ONLY.name,
            notificationMuted = true,
            updatedAt = NOW.toString(),
        )

        val effective = ReleaseCheckPolicy.effective(global, override)

        assertFalse(effective.enabled)
        assertEquals(ReleaseCheckScheduleMode.INTERVAL.name, effective.scheduleMode)
        assertEquals(3, effective.intervalHours)
        assertEquals(8 * 60, effective.dailyLocalMinute)
        assertEquals(ReleaseCheckChannel.STABLE_ONLY.name, effective.releaseChannel)
        assertEquals(ReleaseCheckNetworkPolicy.ANY_AVAILABLE.name, effective.networkPolicy)
        assertEquals(ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name, effective.batteryPolicy)
        assertTrue(effective.notificationMuted)

        assertFalse(
            ReleaseCheckPolicy.effective(
                global.copy(enabled = false),
                override.copy(enabled = true),
            ).enabled,
        )
    }

    @Test
    fun `validation rejects unsupported enum and out of range schedule values`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseCheckPolicy.validate(settings(intervalHours = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseCheckPolicy.validate(settings(intervalHours = 25))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseCheckPolicy.validate(settings(dailyLocalMinute = -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseCheckPolicy.validate(settings(dailyLocalMinute = 1_440))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseCheckPolicy.validate(settings(networkPolicy = "METERED_ONLY"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReleaseCheckPolicy.effective(
                settings(),
                AppReleaseCheckOverrideEntity(
                    registeredAppId = "app",
                    batteryPolicy = "BELOW_20_PERCENT",
                    updatedAt = NOW.toString(),
                ),
            )
        }
    }

    @Test
    fun `interval due time is measured from terminal time`() {
        val effective = ReleaseCheckPolicy.effective(
            settings(scheduleMode = ReleaseCheckScheduleMode.INTERVAL.name, intervalHours = 6),
            null,
        )

        assertEquals(
            Instant.parse("2026-09-06T06:00:00Z"),
            ReleaseCheckPolicy.nextTerminalTime(effective, NOW, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `daily due time uses local timezone and rolls to the next day after the configured time`() {
        val effective = ReleaseCheckPolicy.effective(
            settings(
                scheduleMode = ReleaseCheckScheduleMode.DAILY_LOCAL_TIME.name,
                dailyLocalMinute = 7 * 60,
            ),
            null,
        )
        val zone = ZoneId.of("Asia/Tokyo")

        assertEquals(
            Instant.parse("2026-09-06T22:00:00Z"),
            ReleaseCheckPolicy.nextTerminalTime(effective, NOW, zone),
        )
        assertEquals(
            Instant.parse("2026-09-07T22:00:00Z"),
            ReleaseCheckPolicy.nextTerminalTime(
                effective,
                Instant.parse("2026-09-06T23:00:00Z"),
                zone,
            ),
        )
    }

    @Test
    fun `network and battery gates are fail closed at exactly twenty percent`() {
        val unmetered = ReleaseCheckPolicy.effective(
            settings(
                networkPolicy = ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name,
                batteryPolicy = ReleaseCheckBatteryPolicy.ABOVE_20_PERCENT.name,
            ),
            null,
        )
        assertEquals(
            "DEFERRED_NETWORK",
            ReleaseCheckPolicy.deferReason(
                unmetered,
                ReleaseCheckDeviceState(networkAvailable = false, networkMetered = false, batteryPercent = 99),
            ),
        )
        assertEquals(
            "DEFERRED_NETWORK",
            ReleaseCheckPolicy.deferReason(
                unmetered,
                ReleaseCheckDeviceState(networkAvailable = true, networkMetered = true, batteryPercent = 99),
            ),
        )
        assertEquals(
            "DEFERRED_BATTERY",
            ReleaseCheckPolicy.deferReason(
                unmetered,
                ReleaseCheckDeviceState(networkAvailable = true, networkMetered = false, batteryPercent = 20),
            ),
        )
        assertNull(
            ReleaseCheckPolicy.deferReason(
                unmetered,
                ReleaseCheckDeviceState(networkAvailable = true, networkMetered = false, batteryPercent = 21),
            ),
        )
        assertEquals(
            "DEFERRED_BATTERY",
            ReleaseCheckPolicy.deferReason(
                unmetered,
                ReleaseCheckDeviceState(networkAvailable = true, networkMetered = false, batteryPercent = null),
            ),
        )
    }

    @Test
    fun `charging gate is inherited overridden and fail closed when state is unknown`() {
        val global = settings(requiresCharging = true)

        assertTrue(ReleaseCheckPolicy.effective(global, null).requiresCharging)
        assertFalse(
            ReleaseCheckPolicy.effective(
                global,
                AppReleaseCheckOverrideEntity(
                    registeredAppId = "app",
                    requiresCharging = false,
                    updatedAt = NOW.toString(),
                ),
            ).requiresCharging,
        )

        val effective = ReleaseCheckPolicy.effective(global, null)
        assertEquals(
            "DEFERRED_CHARGING",
            ReleaseCheckPolicy.deferReason(
                effective,
                ReleaseCheckDeviceState(true, false, 100, isCharging = null),
            ),
        )
        assertEquals(
            "DEFERRED_CHARGING",
            ReleaseCheckPolicy.deferReason(
                effective,
                ReleaseCheckDeviceState(true, false, 100, isCharging = false),
            ),
        )
        assertNull(
            ReleaseCheckPolicy.deferReason(
                effective,
                ReleaseCheckDeviceState(true, false, 100, isCharging = true),
            ),
        )
    }

    private fun settings(
        enabled: Boolean = true,
        scheduleMode: String = ReleaseCheckScheduleMode.INTERVAL.name,
        intervalHours: Int = 6,
        dailyLocalMinute: Int = 7 * 60,
        releaseChannel: String = ReleaseCheckChannel.STABLE_ONLY.name,
        networkPolicy: String = ReleaseCheckNetworkPolicy.UNMETERED_ONLY.name,
        batteryPolicy: String = ReleaseCheckBatteryPolicy.ANY.name,
        requiresCharging: Boolean = false,
    ) = ReleaseCheckSettingsEntity(
        enabled = enabled,
        scheduleMode = scheduleMode,
        intervalHours = intervalHours,
        dailyLocalMinute = dailyLocalMinute,
        releaseChannel = releaseChannel,
        networkPolicy = networkPolicy,
        batteryPolicy = batteryPolicy,
        requiresCharging = requiresCharging,
        updatedAt = NOW.toString(),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-06T00:00:00Z")
    }
}
