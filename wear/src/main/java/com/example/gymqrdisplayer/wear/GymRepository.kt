package com.example.gymqrdisplayer.wear

import android.graphics.Bitmap
import android.graphics.Color
import com.example.gymqrdisplayer.core.GymApiClient
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GymRepository {

    companion object {
        val instance: GymRepository by lazy { GymRepository() }
        internal val TAG = "WearGymRepo"

        private val uuidMutex = Mutex()
        @Volatile private var cachedUuid: String? = null
        @Volatile private var uuidCachedAt: Long = 0L
        private const val UUID_TTL_MS = 15 * 60 * 1000L
    }

    /** 完整流程（含 UUID 快取）：給 Tile 用，失敗回傳 null */
    suspend fun fetchQrForUser(uid: String, pwd: String): String? {
        val uuid = uuidMutex.withLock {
            val now = System.currentTimeMillis()
            if (cachedUuid != null && now - uuidCachedAt < UUID_TTL_MS) {
                cachedUuid
            } else {
                val hashCode = getHashCode() ?: return@withLock null
                login(uid, pwd, hashCode)?.also {
                    cachedUuid = it
                    uuidCachedAt = now
                }
            }
        } ?: return null
        return generateQRCode(uuid) ?: run {
            cachedUuid = null // UUID 可能失效，下次重新登入
            null
        }
    }

    private val api = GymApiClient(tag = TAG, connectTimeoutSecs = 20, readTimeoutSecs = 20)

    suspend fun getHashCode() = api.getHashCode()
    suspend fun login(uid: String, pwd: String, hashCode: String) = api.login(uid, pwd, hashCode)
    suspend fun generateQRCode(uuid: String) = api.generateQRCode(uuid)

    // 手錶螢幕版本：尺寸縮小為 300，使用 ARGB_8888 確保 AMOLED 白底正確
    suspend fun createQRCodeBitmap(content: String): Bitmap = withContext(Dispatchers.Default) {
        val hints = mutableMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300, hints)
        val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until bitMatrix.width)
            for (y in 0 until bitMatrix.height)
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
        bitmap
    }

    // Tile 專用版本：較小尺寸、RGB_565 節省記憶體
    suspend fun createQRCodeBitmapForTile(content: String): Bitmap = withContext(Dispatchers.Default) {
        val hints = mutableMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 200, 200, hints)
        val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until bitMatrix.width)
            for (y in 0 until bitMatrix.height)
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
        bitmap
    }
}
