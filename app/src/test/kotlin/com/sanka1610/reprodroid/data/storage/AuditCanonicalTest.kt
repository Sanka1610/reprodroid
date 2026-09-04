package com.sanka1610.reprodroid.data.storage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test
import java.security.MessageDigest

class AuditCanonicalTest {
    @Test
    fun `record and bundle match independent JCS known vector`() {
        val payload = buildJsonObject {
            put("id", "00000000-0000-4000-8000-000000000001")
            put("bytes", "1")
        }
        val recordSha = AuditCanonical.recordSha256("availability", payload)
        assertEquals("ab9a849d87b7ff082322af45ca9774d83e1880f623445a7a51e1da37ebc9102e", recordSha)
        val record = buildJsonObject {
            put("type", "availability")
            put("schemaVersion", 1)
            put("payload", payload)
            put("sha256", recordSha)
        }
        val scope = buildJsonObject {
            put("type", "APP")
            put(
                "registeredAppIds",
                JsonArray(listOf(JsonPrimitive("00000000-0000-4000-8000-000000000002"))),
            )
        }

        val bundle = AuditCanonical.bundle(scope, listOf(record), "2026-09-02T00:00:00Z")

        assertEquals("ee0269dfdba410d9ab0e69472425002a1739a1ada5355a5d9b2cf857f1a090f4", bundle.payloadSha256)
        assertEquals("071d0a872b99df71957e74038b6279376079a4f56026a0012d5d242e6ad4d5bf", sha256(bundle.bytes))
    }

    @Test
    fun `generated time changes bundle bytes but not payload hash`() {
        val scope = buildJsonObject {
            put("type", "ALL")
            put("registeredAppIds", JsonArray(emptyList()))
        }
        val first = AuditCanonical.bundle(scope, emptyList(), "2026-09-02T00:00:00Z")
        val second = AuditCanonical.bundle(scope, emptyList(), "2026-09-02T00:00:01Z")

        assertEquals(first.payloadSha256, second.payloadSha256)
        assertFalse(first.bytes.contentEquals(second.bytes))
    }

    @Test
    fun `audit limits accept exact boundaries and reject one above without truncation`() {
        AuditExportLimits.requireWithin(
            AuditExportLimits.MAX_RECORDS,
            AuditExportLimits.MAX_PAYLOAD_BYTES,
        )

        assertEquals(
            "EXPORT_LIMIT_EXCEEDED",
            assertThrows(IllegalStateException::class.java) {
                AuditExportLimits.requireWithin(AuditExportLimits.MAX_RECORDS + 1, 0)
            }.message,
        )
        assertEquals(
            "EXPORT_LIMIT_EXCEEDED",
            assertThrows(IllegalStateException::class.java) {
                AuditExportLimits.requireWithin(0, AuditExportLimits.MAX_PAYLOAD_BYTES + 1)
            }.message,
        )
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
