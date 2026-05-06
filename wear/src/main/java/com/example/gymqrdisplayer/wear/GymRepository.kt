package com.example.gymqrdisplayer.wear

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GymRepository {

    companion object {
        val instance: GymRepository by lazy { GymRepository() }
        internal val TAG = "WearGymRepo"

        private val client by lazy {
            OkHttpClient.Builder()
                .cookieJar(object : CookieJar {
                    private val cookieStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()

                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        val hostCookies = cookieStore.computeIfAbsent(url.host) { ConcurrentHashMap() }
                        cookies.forEach { hostCookies[it.name] = it }
                    }

                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                        return cookieStore[url.host]?.values?.toList().orEmpty()
                    }
                })
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .apply {
                    if (BuildConfig.DEBUG) {
                        val logging = HttpLoggingInterceptor { msg -> Log.d(TAG, "[HTTP] $msg") }
                        logging.level = HttpLoggingInterceptor.Level.BODY
                        addInterceptor(logging)
                    }
                }
                .build()
        }
    }

    private val clientID = "83137586U1"

    suspend fun getHashCode(): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "1. Fetching HashCode...")
            val request = Request.Builder()
                .url("https://infapp.eip.tw/infinitefit/login?dlogin=n")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Response Code: ${response.code}")
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                val hashCode = doc.select("input[name=HashCode]").`val`()
                if (hashCode.isNotBlank()) {
                    Log.d(TAG, "HashCode fetched: $hashCode")
                } else {
                    Log.d(TAG, "HashCode element not found in HTML (This is OK, we just need the session cookie)")
                }
                hashCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHashCode Error", e)
            null
        }
    }

    suspend fun login(uid: String, pwd: String, hashCode: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "2. Logging in...")
            val formBody = FormBody.Builder()
                .add("ClientID", clientID)
                .add("HashCode", hashCode)
                .add("loginWith", "1")
                .add("uid", uid)
                .add("pwd", pwd)
                .build()

            val request = Request.Builder()
                .url("https://infapp.eip.tw/apis/_appinf3/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://infapp.eip.tw/infinitefit/login?dlogin=n")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val json = JSONObject(responseBody)
                if (json.optString("Status") == "Success") {
                    Log.d(TAG, "Login success")
                    json.getString("uuid")
                } else {
                    Log.e(TAG, "Login failed: ${json.optString("Msg")}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login Error", e)
            null
        }
    }

    suspend fun generateQRCode(uuid: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "3. Generating QR Code...")
            val formBody = FormBody.Builder()
                .add("ClientID", clientID)
                .add("lang", "tw")
                .add("CountDownSec", "8")
                .add("uuid", uuid)
                .build()

            val request = Request.Builder()
                .url("https://infapp.eip.tw/apis/_appinf3/genQRCode")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://infapp.eip.tw/infinitefit/index")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val json = JSONObject(responseBody)
                if (json.optString("Status") == "Success") {
                    json.getString("QRCode")
                } else {
                    Log.e(TAG, "generateQRCode failed: status=${json.optString("Status")} msg=${json.optString("Msg")} raw=$responseBody")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateQRCode Error", e)
            null
        }
    }

    // 手錶螢幕版本：尺寸縮小為 300，使用 ARGB_8888 確保 AMOLED 白底正確
    suspend fun createQRCodeBitmap(content: String): Bitmap = withContext(Dispatchers.Default) {
        val writer = QRCodeWriter()
        val hints = mutableMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.Q

        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    }

    // Tile 專用版本：較小尺寸、RGB_565 節省記憶體
    suspend fun createQRCodeBitmapForTile(content: String): Bitmap = withContext(Dispatchers.Default) {
        val writer = QRCodeWriter()
        val hints = mutableMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.Q

        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 200, 200, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    }
}
