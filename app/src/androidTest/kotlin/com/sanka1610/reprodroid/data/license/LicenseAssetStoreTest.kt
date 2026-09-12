package com.sanka1610.reprodroid.data.license

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicenseAssetStoreTest {
    @Test
    fun bundledLicenseAndThirdPartyNoticesAreReadableOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val documents = LicenseAssetStore(context).loadDocuments()

        assertEquals(5, documents.size)
        assertTrue(documents[0].text.contains("Apache License"))
        assertTrue(documents[0].text.contains("Copyright 2026 Sanka1610"))
        assertTrue(documents[1].text.contains("ReproDroid third-party notices"))
        assertTrue(documents[1].text.contains("androidx.core:core-ktx 1.17.0"))
        assertTrue(documents[1].text.replace(Regex("\\s+"), " ").contains("Each dependency remains"))
        assertTrue(documents[2].text.contains("Copyright (c) 2010 Ben Gruver"))
        assertTrue(documents[2].text.contains("THIS SOFTWARE IS PROVIDED BY THE AUTHOR"))
        assertTrue(documents[2].text.contains("Copyright 2011, Google LLC"))
        assertTrue(documents[2].text.contains("Copyright (C) 2007 The Android Open Source Project"))
        assertTrue(documents[2].text.contains("https://github.com/google/guava"))
        assertTrue(documents[2].text.contains("Version 2.0, January 2004"))
        assertTrue(documents[3].text.contains("Copyright 2004-present by the Checker Framework developers"))
        assertTrue(documents[3].text.contains("THE SOFTWARE IS PROVIDED \"AS IS\""))
        assertTrue(documents[4].text.contains("Copyright (c) 2004-2022 QOS.ch Sarl"))
        assertTrue(documents[4].text.contains("Permission is hereby granted"))
    }
}
