package com.example.gymqrdisplayer.core

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GymApiClient(
    private val tag: String = "GymApiClient",
    connectTimeoutSecs: Long = 15,
    readTimeoutSecs: Long = 15
) {
    companion object {
        private const val CLIENT_ID = "83137586U1"
        private const val BASE_URL = "https://infapp.eip.tw"
        private const val API_PATH = "_appinf4"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val DNS_ATTEMPTS = 3
        private const val DNS_RETRY_DELAY_MS = 500L
    }

    // [diag] 背景被喚醒時 DNS 可能立刻失敗（UnknownHostException）。隔 0.5 秒重試，
    // 用來分辨「短暫空窗」（重試後成功）與「持續被擋」（三次都失敗）。
    private suspend fun <T> retryOnDns(label: String, block: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                val result = block()
                if (attempt > 1) Log.w(tag, "$label: succeeded on attempt $attempt/$DNS_ATTEMPTS")
                return result
            } catch (e: UnknownHostException) {
                Log.w(tag, "$label: DNS failed (attempt $attempt/$DNS_ATTEMPTS): ${e.message}")
                if (attempt >= DNS_ATTEMPTS) throw e
                attempt++
                delay(DNS_RETRY_DELAY_MS)
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val cookieStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val hostCookies = cookieStore.computeIfAbsent(url.host) { ConcurrentHashMap() }
                cookies.forEach { hostCookies[it.name] = it }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookieStore[url.host]?.values?.toList().orEmpty()
        })
        .connectTimeout(connectTimeoutSecs, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSecs, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor { msg -> Log.d(tag, "[HTTP] $msg") }
                logging.level = HttpLoggingInterceptor.Level.BODY
                addInterceptor(logging)
            }
        }
        .build()

    suspend fun getHashCode(): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "1. Fetching HashCode...")
            retryOnDns("getHashCode") {
                // Prime session cookies via the login page, then fetch HashCode from the Initial API
                // (the login page no longer embeds HashCode in its HTML).
                client.newCall(
                    Request.Builder()
                        .url("$BASE_URL/infinitefit/login?dlogin=n")
                        .header("User-Agent", USER_AGENT)
                        .build()
                ).execute().close()
                val request = Request.Builder()
                    .url("$BASE_URL/apis/$API_PATH/Initial")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "$BASE_URL/infinitefit/login?dlogin=n")
                    .post(FormBody.Builder().add("lang", "tw").build())
                    .build()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    val hashCode = json.optString("HashCode")
                    if (json.optString("Status") == "Success" && hashCode.isNotBlank()) {
                        Log.d(tag, "HashCode fetched")
                        hashCode
                    } else {
                        Log.e(tag, "getHashCode failed: raw=$responseBody")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "getHashCode Error: ${e.javaClass.name}: ${e.message}", e)
            null
        }
    }

    suspend fun login(uid: String, pwd: String, hashCode: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "2. Logging in...")
            val formBody = FormBody.Builder()
                .add("ClientID", CLIENT_ID)
                .add("HashCode", hashCode)
                .add("loginWith", "1")
                .add("uid", uid)
                .add("pwd", pwd)
                .build()
            val request = Request.Builder()
                .url("$BASE_URL/apis/$API_PATH/login")
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$BASE_URL/infinitefit/login?dlogin=n")
                .post(formBody)
                .build()
            retryOnDns("login") {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    if (json.optString("Status") == "Success") {
                        Log.d(tag, "Login success")
                        json.getString("uuid")
                    } else {
                        Log.e(tag, "Login failed: ${json.optString("Message", json.optString("Msg"))}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Login Error: ${e.javaClass.name}: ${e.message}", e)
            null
        }
    }

    suspend fun generateQRCode(uuid: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "3. Generating QR Code...")
            val formBody = FormBody.Builder()
                .add("ClientID", CLIENT_ID)
                .add("lang", "tw")
                .add("CountDownSec", "60")
                .add("uuid", uuid)
                .build()
            val request = Request.Builder()
                .url("$BASE_URL/apis/$API_PATH/genQRCode")
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$BASE_URL/infinitefit/index")
                .post(formBody)
                .build()
            retryOnDns("generateQRCode") {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    if (json.optString("Status") == "Success") {
                        json.getString("QRCode")
                    } else {
                        Log.e(tag, "generateQRCode failed: status=${json.optString("Status")} msg=${json.optString("Msg")} raw=$responseBody")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "generateQRCode Error: ${e.javaClass.name}: ${e.message}", e)
            null
        }
    }
}
