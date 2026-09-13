package com.sanka1610.reprodroid.ui.state

import com.sanka1610.reprodroid.ui.navigation.ReproDroidRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedUiEventTest {
    @Test
    fun `matching origin and identity returns the typed destination`() {
        val result = appResult(
            origin = ReproDroidRoute.AppEdit(APP_ID),
            destination = ReproDroidRoute.AppInformation(APP_ID),
        )

        val disposition = result.relevantDisposition(ReproDroidRoute.AppEdit(APP_ID), null)

        assertEquals(ReproDroidRoute.AppInformation(APP_ID), disposition?.destination)
    }

    @Test
    fun `late completion after leaving the origin route is ignored`() {
        val result = appResult(
            origin = ReproDroidRoute.AppEdit(APP_ID),
            destination = ReproDroidRoute.AppInformation(APP_ID),
        )

        assertNull(result.relevantDisposition(ReproDroidRoute.Apps, null))
    }

    @Test
    fun `result cannot be applied while another app identity is visible`() {
        val result = appResult(
            origin = ReproDroidRoute.AppEdit(APP_ID),
            destination = ReproDroidRoute.AppInformation(APP_ID),
        )

        assertNull(result.relevantDisposition(ReproDroidRoute.AppEdit(OTHER_APP_ID), null))
    }

    @Test
    fun `removal result requires the original dialog target`() {
        val result = appResult(
            origin = ReproDroidRoute.AppInformation(APP_ID),
            destination = ReproDroidRoute.Apps,
        ).copy(requiresRemovalTarget = true, clearRemovalTarget = true)

        assertNull(result.relevantDisposition(ReproDroidRoute.AppInformation(APP_ID), OTHER_APP_ID))
        assertEquals(
            ReproDroidRoute.Apps,
            result.relevantDisposition(ReproDroidRoute.AppInformation(APP_ID), APP_ID)?.destination,
        )
    }

    @Test
    fun `release result requires the latest candidate identity`() {
        val result = ManagedUiResult(
            owner = ManagedUiOwner.RELEASE,
            kind = ManagedUiResultKind.RELEASE_CANDIDATE_READY,
            originRoute = ReproDroidRoute.UpdateSettings.encode(),
            destination = ReproDroidRoute.AppTechnical(APP_ID),
            registeredAppId = APP_ID,
            candidateId = "candidate-a",
        )

        assertNull(
            result.relevantDisposition(
                ReproDroidRoute.UpdateSettings,
                removalTargetId = null,
                pendingCandidateId = "candidate-b",
            ),
        )
        assertEquals(
            ReproDroidRoute.AppTechnical(APP_ID),
            result.relevantDisposition(
                ReproDroidRoute.UpdateSettings,
                removalTargetId = null,
                pendingCandidateId = "candidate-a",
            )?.destination,
        )
    }

    @Test
    fun `event store consumes only the acknowledged identity and does not replay after recreation`() {
        val store = ManagedUiEventStore()
        store.publishResult(
            appResult(
                origin = ReproDroidRoute.AppEdit(APP_ID),
                destination = ReproDroidRoute.AppInformation(APP_ID),
            ),
        )
        store.publishResult(
            appResult(
                origin = ReproDroidRoute.AppSettings(APP_ID),
                destination = ReproDroidRoute.AppInformation(APP_ID),
            ),
        )
        val first = store.results.value.first()

        store.acknowledgeResult(first.id)

        assertEquals(1, store.results.value.size)
        assertTrue(ManagedUiEventStore().results.value.isEmpty())
    }

    @Test
    fun `a newer message is not cleared by a stale acknowledgement`() {
        val store = ManagedUiEventStore()
        store.publishMessage(ManagedUiOwner.APPS, "first")
        val firstId = requireNotNull(store.message.value).id
        store.publishMessage(ManagedUiOwner.RELEASE, "second")

        store.acknowledgeMessage(firstId)

        assertEquals("second", store.message.value?.text)
        assertEquals(ManagedUiOwner.RELEASE, store.message.value?.owner)
        assertEquals(ManagedUiMessageCode.OPERATION_FAILED, store.message.value?.code)
    }

    @Test
    fun `message mapping publishes a bounded presentation code`() {
        val store = ManagedUiEventStore()

        store.publishMessage(ManagedUiOwner.REGISTRATION, "Provider rate limit reached")

        assertEquals(ManagedUiMessageCode.RATE_LIMIT, store.message.value?.code)
    }

    private fun appResult(origin: ReproDroidRoute, destination: ReproDroidRoute) = ManagedUiResult(
        owner = ManagedUiOwner.APP_DETAIL,
        kind = ManagedUiResultKind.METADATA_SAVED,
        originRoute = origin.encode(),
        destination = destination,
        registeredAppId = APP_ID,
    )

    private companion object {
        const val APP_ID = "app-a"
        const val OTHER_APP_ID = "app-b"
    }
}
