package com.sanka1610.reprodroid.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sanka1610.reprodroid.data.network.RunnerApiClient
import com.sanka1610.reprodroid.data.storage.RunnerRetentionCoordinator
import com.sanka1610.reprodroid.data.storage.RunnerStorageConnectionStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RunnerRetentionCoordinatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun capabilityAndRunnerIdentityMismatchFailClosedWithoutDroppingLocalHold() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val oldRunnerId = UUID.randomUUID().toString()
        try {
            database.storageDao().upsertRetentionHold(
                RetentionHoldEntity(
                    holdId = UUID.randomUUID().toString(),
                    runnerId = oldRunnerId,
                    principalId = "local-development",
                    resourceKind = "JOB",
                    resourceId = UUID.randomUUID().toString(),
                    reason = "CURRENT_COMPARISON",
                    clientReferenceType = "COMPARISON",
                    clientReferenceId = UUID.randomUUID().toString(),
                    requestSha256 = "a".repeat(64),
                    state = RetentionHoldState.ACTIVE.name,
                    createdOperationId = UUID.randomUUID().toString(),
                    releasedOperationId = null,
                    createdAt = Instant.now().toString(),
                    releasedAt = null,
                ),
            )

            val incompatible = coordinator(
                database,
                MockEngine {
                    respond(
                        capabilities(oldRunnerId, includeStorage = false),
                        HttpStatusCode.OK,
                        JSON_HEADERS,
                    )
                },
            )
            incompatible.syncCurrentComparisonHolds()
            assertEquals(RunnerStorageConnectionStatus.INCOMPATIBLE, incompatible.state.value.status)
            assertEquals(1, database.storageDao().getActiveRetentionHolds().size)

            val changedRunnerId = UUID.randomUUID().toString()
            val changed = coordinator(
                database,
                MockEngine {
                    respond(capabilities(changedRunnerId), HttpStatusCode.OK, JSON_HEADERS)
                },
            )
            changed.syncCurrentComparisonHolds()
            assertEquals(RunnerStorageConnectionStatus.RUNNER_CHANGED, changed.state.value.status)
            assertEquals(changedRunnerId, changed.state.value.runnerId)
            assertEquals(1, database.storageDao().getActiveRetentionHolds().size)

            val unavailable = coordinator(
                database,
                MockEngine { respondError(HttpStatusCode.ServiceUnavailable) },
            )
            unavailable.syncCurrentComparisonHolds()
            assertEquals(RunnerStorageConnectionStatus.UNAVAILABLE, unavailable.state.value.status)
            assertNotNull(database.storageDao().getActiveRetentionHolds().single())
        } finally {
            database.close()
        }
    }

    @Test
    fun summaryRunnerIdentityChangeDoesNotBecomeAvailable() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReproDroidDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val capabilityRunnerId = UUID.randomUUID().toString()
        val summaryRunnerId = UUID.randomUUID().toString()
        var request = 0
        try {
            val coordinator = coordinator(
                database,
                MockEngine {
                    request++
                    if (request == 1) {
                        respond(capabilities(capabilityRunnerId), HttpStatusCode.OK, JSON_HEADERS)
                    } else {
                        respond(summary(summaryRunnerId), HttpStatusCode.OK, JSON_HEADERS)
                    }
                },
            )

            coordinator.syncCurrentComparisonHolds()

            assertEquals(RunnerStorageConnectionStatus.RUNNER_CHANGED, coordinator.state.value.status)
            assertEquals(summaryRunnerId, coordinator.state.value.runnerId)
        } finally {
            database.close()
        }
    }

    private fun coordinator(database: ReproDroidDatabase, engine: MockEngine) = RunnerRetentionCoordinator(
        database,
        RunnerApiClient("http://127.0.0.1:8080", engine, allowDevelopmentV2 = true),
    )

    private fun capabilities(runnerId: String, includeStorage: Boolean = true): String =
        """{"apiVersion":"v2","foundationContractVersion":1,"runnerId":"$runnerId","runnerVersion":"test","capabilities":[{"id":"foundation","contractVersion":1}${if (includeStorage) ",{\"id\":\"storage-retention\",\"contractVersion\":1}" else ""}]}"""

    private fun summary(runnerId: String): String =
        """{"schemaVersion":1,"runnerId":"$runnerId","areas":[{"area":"RUNNER_JOB","budgetBytes":"68719476736","usedBytes":"0","reservedBytes":"0","unclassifiedBytes":"0","usableBytes":"1073741824","warningPercent":80,"state":"OK","measurementState":"COMPLETE","measuredAt":"2026-09-04T00:00:00Z"},{"area":"RUNNER_TOOLCHAIN","budgetBytes":"34359738368","usedBytes":"0","reservedBytes":"0","unclassifiedBytes":"0","usableBytes":"1073741824","warningPercent":80,"state":"OK","measurementState":"COMPLETE","measuredAt":"2026-09-04T00:00:00Z"}]}"""

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
