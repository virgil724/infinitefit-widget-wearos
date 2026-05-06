package com.example.gymqrdisplayer.wear

import android.graphics.Bitmap
import android.graphics.Color
import com.example.gymqrdisplayer.core.GymApiClient
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GymRepository {

    companion object {
        val instance: GymRepository by lazy { GymRepository() }
        internal val TAG = "WearGymRepo"
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
