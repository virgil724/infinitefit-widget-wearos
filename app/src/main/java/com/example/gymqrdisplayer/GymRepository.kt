package com.example.gymqrdisplayer

import android.graphics.Bitmap
import android.graphics.Color
import com.example.gymqrdisplayer.core.GymApiClient
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GymRepository {

    companion object {
        val instance: GymRepository by lazy { GymRepository() }

        private val uuidMutex = Mutex()
        @Volatile private var cachedUuid: String? = null
        @Volatile private var uuidCachedAt: Long = 0L
        private const val UUID_TTL_MS = 15 * 60 * 1000L

        fun cacheUuid(uuid: String) {
            cachedUuid = uuid
            uuidCachedAt = System.currentTimeMillis()
        }

        fun invalidateUuid() {
            cachedUuid = null
        }
    }

    private val api = GymApiClient(tag = "GymRepo", connectTimeoutSecs = 15, readTimeoutSecs = 15)

    suspend fun getHashCode() = api.getHashCode()
    suspend fun login(uid: String, pwd: String, hashCode: String) = api.login(uid, pwd, hashCode)
    suspend fun generateQRCode(uuid: String) = api.generateQRCode(uuid)

    suspend fun generateQrCodeForUser(uid: String, pwd: String): String? {
        val uuid = uuidMutex.withLock {
            val now = System.currentTimeMillis()
            if (cachedUuid != null && (now - uuidCachedAt) < UUID_TTL_MS) {
                cachedUuid
            } else {
                val hashCode = getHashCode() ?: return@withLock null
                val newUuid = login(uid, pwd, hashCode) ?: return@withLock null
                cachedUuid = newUuid
                uuidCachedAt = now
                newUuid
            }
        } ?: return null
        return generateQRCode(uuid)
    }

    fun createQRCodeBitmap(content: String): Bitmap {
        val hints = mutableMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until bitMatrix.width)
            for (y in 0 until bitMatrix.height)
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
        return bitmap
    }
}
