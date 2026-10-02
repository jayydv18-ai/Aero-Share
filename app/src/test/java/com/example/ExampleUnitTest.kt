package com.example

import com.example.aeroshare.data.model.AvatarPresets
import com.example.aeroshare.data.model.QrSessionPayload
import com.example.aeroshare.qr.QrManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun avatarPresets_containsAtLeastTenOptions() {
        val options = AvatarPresets.options
        assertTrue(options.size >= 10)
        assertNotNull(AvatarPresets.getById("avatar_1"))
    }

    @Test
    fun qrManager_parseValidSessionPayload() {
        val payload = QrSessionPayload(
            serviceId = "com.aistudio.aeroshare.p2p",
            endpointId = "test_endpoint_123",
            deviceName = "Pixel 9 Pro",
            avatarId = "avatar_3",
            pin = "4829",
            timestamp = System.currentTimeMillis()
        )

        val jsonString = """
            {
               "service": "${payload.serviceId}",
               "endpoint": "${payload.endpointId}",
               "name": "${payload.deviceName}",
               "avatar": "${payload.avatarId}",
               "pin": "${payload.pin}",
               "ts": ${payload.timestamp}
            }
        """.trimIndent()

        val result = QrManager.parseQrContent(jsonString)
        assertTrue(result is QrManager.QrParseResult.Success)
        val success = result as QrManager.QrParseResult.Success
        assertEquals("test_endpoint_123", success.payload.endpointId)
        assertEquals("Pixel 9 Pro", success.payload.deviceName)
        assertEquals("4829", success.payload.pin)
    }

    @Test
    fun qrManager_rejectsExpiredPayload() {
        val oldTimestamp = System.currentTimeMillis() - (10 * 60 * 1000L) // 10 minutes ago
        val jsonString = """
            {
               "service": "com.aistudio.aeroshare.p2p",
               "endpoint": "old_endpoint",
               "name": "Old Phone",
               "avatar": "avatar_1",
               "pin": "1234",
               "ts": $oldTimestamp
            }
        """.trimIndent()

        val result = QrManager.parseQrContent(jsonString)
        assertTrue(result is QrManager.QrParseResult.Expired)
    }
}
