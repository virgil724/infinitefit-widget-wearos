package com.example.gymqrdisplayer

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
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GymRepository {
    private val TAG = "GymRepo"

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val cookieStore = mutableMapOf<String, MutableMap<String, Cookie>>()
            
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val hostCookies = cookieStore.getOrPut(url.host) { mutableMapOf() }
                cookies.forEach { hostCookies[it.name] = it }
                Log.d(TAG, "Saved Cookies: ${cookies.joinToString { "${it.name}=${it.value}" }}")
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val cookies = cookieStore[url.host]?.values?.toList() ?: listOf()
                Log.d(TAG, "Loading Cookies for ${url.host}: ${cookies.size} found")
                return cookies
            }
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val clientID = "83137586U1"

    suspend fun getHashCode(): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "1. Fetching HashCode...")
            val request = Request.Builder()
                .url("https://infapp.eip.tw/infinitefit/login?dlogin=n")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                val hashCode = doc.select("input[name=HashCode]").`val`()
                Log.d(TAG, "HashCode Result: $hashCode")
                hashCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHashCode Error", e)
            null
        }
    }

    suspend fun login(uid: String, pwd: String, hashCode: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "2. Logging in (UID: $uid)...")
            val formBody = FormBody.Builder()
                .add("ClientID", clientID)
                .add("HashCode", hashCode)
                .add("loginWith", "1")
                .add("uid", uid)
                .add("pwd", pwd)
                .build()

            val request = Request.Builder()
                .url("https://infapp.eip.tw/apis/_appinf/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://infapp.eip.tw/infinitefit/login?dlogin=n")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Login Response: $responseBody")
                val json = JSONObject(responseBody)
                if (json.optString("Status") == "Success") {
                    val uuid = json.getString("uuid")
                    Log.d(TAG, "Login Success, UUID: $uuid")
                    uuid
                } else {
                    Log.e(TAG, "Login Failed: ${json.optString("Msg")}")
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
                .url("https://infapp.eip.tw/apis/_appinf/genQRCode")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://infapp.eip.tw/infinitefit/index")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "QR Response: $responseBody")
                val json = JSONObject(responseBody)
                if (json.optString("Status") == "Success") {
                    json.getString("QRCode")
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateQRCode Error", e)
            null
        }
    }

    fun createQRCodeBitmap(content: String): Bitmap {
        val writer = QRCodeWriter()
        val hints = mutableMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.Q

        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
