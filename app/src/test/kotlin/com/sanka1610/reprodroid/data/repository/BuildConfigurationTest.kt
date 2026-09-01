package com.sanka1610.reprodroid.data.repository

import com.sanka1610.reprodroid.data.local.BuildConfigurationValidationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.erdtman.jcs.JsonCanonicalizer

class BuildConfigurationTest {
    @Test
    fun `configured payload is canonicalized and hashed deterministically`() {
        val input = BuildConfigurationInput(
            buildRoot = "android",
            modulePath = ":app",
            variant = "release",
            tasks = listOf(":app:assembleRelease"),
            javaMajor = 21,
            gradleVersion = "9.1.0",
            compileSdk = 36,
            buildToolsVersion = "36.0.0",
        )

        val first = BuildConfigurationValidator.validate(input)
        val second = BuildConfigurationValidator.validate(input.copy())

        assertEquals(BuildConfigurationValidationState.CONFIGURED, first.validationState)
        assertEquals(first.canonicalJson, second.canonicalJson)
        assertEquals(first.contentSha256, second.contentSha256)
        assertTrue(first.contentSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(first.canonicalJson.contains("\"ndkVersion\":null"))
        assertEquals(input, BuildConfigurationValidator.decodeCanonical(first.canonicalJson, first.contentSha256))
    }

    @Test
    fun `incomplete payload remains an explicit draft`() {
        val result = BuildConfigurationValidator.validate(BuildConfigurationInput(buildRoot = "."))
        assertEquals(BuildConfigurationValidationState.DRAFT, result.validationState)
    }

    @Test
    fun `path task and mutable version injection are rejected`() {
        listOf(
            BuildConfigurationInput(buildRoot = "../escape"),
            BuildConfigurationInput(tasks = listOf(":app:assembleRelease --offline")),
            BuildConfigurationInput(tasks = listOf(":app:build", ":app:build")),
            BuildConfigurationInput(modulePath = ":" + "module:".repeat(200) + "task"),
            BuildConfigurationInput(gradleVersion = "latest"),
            BuildConfigurationInput(buildToolsVersion = "36.0.0;rm"),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                BuildConfigurationValidator.validate(invalid)
            }
        }
    }

    @Test
    fun `JCS library matches a numeric and object ordering vector`() {
        val input = """{"b":2,"numbers":[333333333.33333329,1E30,4.50,2e-3],"a":1}"""
        val canonical = JsonCanonicalizer(input).encodedString
        assertEquals(
            """{"a":1,"b":2,"numbers":[333333333.3333333,1e+30,4.5,0.002]}""",
            canonical,
        )
    }

    @Test
    fun `JCS library orders Unicode property names by UTF-16 code units`() {
        val input = """{"דּ":1,"😀":2,"€":3,"ö":4,"1":5,"\r":6}"""
        val canonical = JsonCanonicalizer(input).encodedString
        assertEquals("""{"\r":6,"1":5,"ö":4,"€":3,"😀":2,"דּ":1}""", canonical)
    }

    @Test
    fun `stored canonical JSON and hash must agree`() {
        val validated = BuildConfigurationValidator.validate(BuildConfigurationInput(buildRoot = "."))

        assertThrows(IllegalArgumentException::class.java) {
            BuildConfigurationValidator.decodeCanonical(validated.canonicalJson, "0".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BuildConfigurationValidator.decodeCanonical(
                validated.canonicalJson.replace("\"buildRoot\":\".\"", "\"buildRoot\":\"other\""),
                validated.contentSha256,
            )
        }
    }
}
