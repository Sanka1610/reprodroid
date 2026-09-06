package com.sanka1610.reprodroid.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseCheckWorkerTest {
    @Test
    fun workerFailsClosedWhenTheApplicationContextIsNotReproDroidApplication() = runBlocking {
        val worker = TestListenableWorkerBuilder<ReleaseCheckWorker>(
            InstrumentationRegistry.getInstrumentation().context,
        ).build()

        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }
}
