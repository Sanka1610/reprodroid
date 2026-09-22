package com.sanka1610.reprodroid.data.provider

import com.sanka1610.reprodroid.data.local.ReleaseVariantPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceAssetSelectionTest {
    private fun select(names: List<String>, abis: List<String> = listOf("arm64-v8a", "armeabi-v7a"), variant: ReleaseVariantPreference = ReleaseVariantPreference.RELEASE) =
        ReleaseAssetSelector.suggestForDevice(names.map { it to it }, abis, variant)

    @Test fun `device ABI order wins over the filename order`() {
        assertEquals("app-arm64-v8a.apk", select(listOf("app-armeabi-v7a.apk", "app-arm64-v8a.apk", "app-universal.apk")))
        assertEquals("app-x86_64.apk", select(listOf("app-arm64-v8a.apk", "app-x86_64.apk"), listOf("x86_64", "x86")))
    }

    @Test fun `release variant excludes debug and preview`() {
        val names = listOf("app-arm64-v8a-debug.apk", "app-arm64-v8a-preview.apk", "app-arm64-v8a-release.apk")
        assertEquals(names[2], select(names))
        assertEquals(names[1], select(names, variant = ReleaseVariantPreference.PREVIEW))
        assertEquals(names[0], select(names, variant = ReleaseVariantPreference.DEBUG))
    }

    @Test fun `universal and single unlabelled releases can be suggested`() {
        assertEquals("app-universal.apk", select(listOf("app-x86.apk", "app-universal.apk")))
        assertEquals("app.apk", select(listOf("app.apk")))
        assertEquals("app.apk", select(listOf("app.apk"), emptyList()))
    }

    @Test fun `ambiguous variants require choice rather than falling back to another ABI`() {
        assertNull(select(listOf("app-foss-arm64.apk", "app-full-arm64.apk", "app-universal.apk")))
        assertNull(select(listOf("app-one.apk", "app-two.apk")))
    }

    @Test fun `unknown device does not guess an ABI`() {
        assertNull(select(listOf("app-arm64-v8a.apk", "app-x86_64.apk"), emptyList()))
        assertNull(select(listOf("app-arm64-v8a.apk"), listOf("unknown")))
    }

    @Test fun `known incompatible ABI is not suggested even for a single asset`() {
        assertNull(select(listOf("app-x86_64.apk")))
        assertNull(select(listOf("app-x86_64.apk"), listOf("x86")))
        assertNull(select(listOf("app-armeabi-v7a.apk"), listOf("armeabi")))
        assertNull(select(listOf("app-riscv64.apk")))
    }

    @Test fun `ABI aliases match whole tokens only`() {
        assertEquals("app-aarch64.apk", select(listOf("app-aarch64.apk", "app-amd64.apk")))
        assertEquals("app-armv7.apk", select(listOf("app-armv7.apk", "app-amd64.apk")))
        assertNull(select(listOf("app-notarm64.apk", "app-aarch64pro.apk")))
    }
}
