package com.example.aeroshare.qr

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.aeroshare.data.model.QrSessionPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject

object QrManager {
    private const val EXPIRATION_WINDOW_MILLIS = 5 * 60 * 1000L // 5 minutes validity

    fun generateQrBitmap(
        payload: QrSessionPayload,
        size: Int = 512,
        primaryColor: Color = Color(0xFF0F172A),
        backgroundColor: Color = Color.White
    ): Bitmap {
        val json = JSONObject().apply {
            put("service", payload.serviceId)
            put("endpoint", payload.endpointId)
            put("name", payload.deviceName)
            put("avatar", payload.avatarId)
            put("pin", payload.pin)
            put("ts", payload.timestamp)
        }

        val bitMatrix = MultiFormatWriter().encode(
            json.toString(),
            BarcodeFormat.QR_CODE,
            size,
            size
        )

        val pixels = IntArray(size * size)
        val fg = primaryColor.toArgb()
        val bg = backgroundColor.toArgb()

        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (bitMatrix[x, y]) fg else bg
            }
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    sealed class QrParseResult {
        data class Success(val payload: QrSessionPayload) : QrParseResult()
        data class Expired(val ageMillis: Long) : QrParseResult()
        data class Invalid(val reason: String) : QrParseResult()
    }

    fun parseQrContent(rawString: String): QrParseResult {
        return try {
            val json = JSONObject(rawString)
            if (!json.has("service") || !json.has("endpoint") || !json.has("name") || !json.has("ts")) {
                return QrParseResult.Invalid("QR code is not a valid AeroShare session")
            }

            val timestamp = json.getLong("ts")
            val now = System.currentTimeMillis()
            val age = now - timestamp

            if (age < -60_000L || age > EXPIRATION_WINDOW_MILLIS) {
                return QrParseResult.Expired(age)
            }

            val payload = QrSessionPayload(
                serviceId = json.getString("service"),
                endpointId = json.getString("endpoint"),
                deviceName = json.getString("name"),
                avatarId = json.optString("avatar", "avatar_1"),
                pin = json.optString("pin", "0000"),
                timestamp = timestamp
            )
            QrParseResult.Success(payload)
        } catch (e: Exception) {
            QrParseResult.Invalid("Failed to decode QR session: ${e.localizedMessage}")
        }
    }
}
