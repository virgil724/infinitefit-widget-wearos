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
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GymApiClient(
    private val tag: String = "GymApiClient",
    connectTimeoutSecs: Long = 15,
    readTimeoutSecs: Long = 15
) {
    companion object {
        private const val CLIENT_ID = "83137586U1"
        private const val BASE_URL = "https://infapp.eip.tw"
        private const val API_PATH = "_appinf3"
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
            val request = Request.Builder()
                .url("$BASE_URL/infinitefit/login?dlogin=n")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                val hashCode = doc.select("input[name=HashCode]").`val`()
                if (hashCode.isNotBlank()) Log.d(tag, "HashCode fetched")
                hashCode
            }
        } catch (e: Exception) {
            Log.e(tag, "getHashCode Error", e)
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
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$BASE_URL/infinitefit/login?dlogin=n")
                .post(formBody)
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val json = JSONObject(responseBody)
                if (json.optString("Status") == "Success") {
                    Log.d(tag, "Login success")
                    json.getString("uuid")
                } else {
                    Log.e(tag, "Login failed: ${json.optString("Msg")}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Login Error", e)
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
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "$BASE_URL/infinitefit/index")
                .post(formBody)
                .build()
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
        } catch (e: Exception) {
            Log.e(tag, "generateQRCode Error", e)
            null
        }
    }
}
