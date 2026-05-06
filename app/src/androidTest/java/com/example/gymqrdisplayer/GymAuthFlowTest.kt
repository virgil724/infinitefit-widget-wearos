package com.example.gymqrdisplayer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "GymAuthFlowTest"
private const val CLIENT_ID = "83137586U1"
private const val BASE = "https://infapp.eip.tw"

@RunWith(AndroidJUnit4::class)
class GymAuthFlowTest {

    private fun freshClient() = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val store = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val h = store.computeIfAbsent(url.host) { ConcurrentHashMap() }
                cookies.forEach { h[it.name] = it }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                store[url.host]?.values?.toList().orEmpty()
        })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Result(
        val variant: String,
        val failedStep: String,
        val status: String,
        val msg: String,
        val raw: String,
        val qrCode: String?
    )

    private suspend fun runVariant(uid: String, pwd: String, apiPath: String): Result =
        withContext(Dispatchers.IO) {
            val client = freshClient()
            val label = apiPath

            // Step 1: HashCode
            Log.d(TAG, "[$label] Step 1: GET $BASE/infinitefit/login?dlogin=n")
            val hashCode = client.newCall(
                Request.Builder()
                    .url("$BASE/infinitefit/login?dlogin=n")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
            ).execute().use { r ->
                val html = r.body?.string() ?: ""
                Log.d(TAG, "[$label] Step 1 HTTP ${r.code}, html length=${html.length}")
                Jsoup.parse(html).select("input[name=HashCode]").`val`()
            }
            Log.d(TAG, "[$label] HashCode='$hashCode'")

            // Step 2: Login
            Log.d(TAG, "[$label] Step 2: POST $BASE/apis/$apiPath/login")
            val loginRaw = client.newCall(
                Request.Builder()
                    .url("$BASE/apis/$apiPath/login")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "$BASE/infinitefit/login?dlogin=n")
                    .post(
                        FormBody.Builder()
                            .add("ClientID", CLIENT_ID)
                            .add("HashCode", hashCode)
                            .add("loginWith", "1")
                            .add("uid", uid)
                            .add("pwd", pwd)
                            .build()
                    )
                    .build()
            ).execute().use { r ->
                Log.d(TAG, "[$label] Step 2 HTTP ${r.code}")
                r.body?.string() ?: ""
            }
            Log.d(TAG, "[$label] Step 2 RAW: $loginRaw")

            val loginJson = runCatching { JSONObject(loginRaw) }.getOrDefault(JSONObject())
            val loginStatus = loginJson.optString("Status", "PARSE_ERROR")
            val loginMsg = loginJson.optString("Msg", "")
            if (loginStatus != "Success") {
                return@withContext Result(label, "login", loginStatus, loginMsg, loginRaw, null)
            }
            val uuid = loginJson.optString("uuid")

            // Step 3: genQRCode
            Log.d(TAG, "[$label] Step 3: POST $BASE/apis/$apiPath/genQRCode (uuid=$uuid)")
            val qrRaw = client.newCall(
                Request.Builder()
                    .url("$BASE/apis/$apiPath/genQRCode")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "$BASE/infinitefit/index")
                    .post(
                        FormBody.Builder()
                            .add("ClientID", CLIENT_ID)
                            .add("lang", "tw")
                            .add("CountDownSec", "8")
                            .add("uuid", uuid)
                            .build()
                    )
                    .build()
            ).execute().use { r ->
                Log.d(TAG, "[$label] Step 3 HTTP ${r.code}")
                r.body?.string() ?: ""
            }
            Log.d(TAG, "[$label] Step 3 RAW: $qrRaw")

            val qrJson = runCatching { JSONObject(qrRaw) }.getOrDefault(JSONObject())
            val qrStatus = qrJson.optString("Status", "PARSE_ERROR")
            val qrMsg = qrJson.optString("Msg", "")
            val qrCode = if (qrStatus == "Success")
                qrJson.optString("QRCode").takeIf { it.isNotBlank() }
            else null

            Result(label, "genQRCode", qrStatus, qrMsg, qrRaw, qrCode)
        }

    @Test
    fun loginToGenQRCode_bothEndpointVariants() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val uid = args.getString("uid", "")
        val pwd = args.getString("pwd", "")
        assumeFalse(
            "Skipping: pass credentials via -e uid YOUR_UID -e pwd YOUR_PASSWORD",
            uid.isNullOrBlank() || pwd.isNullOrBlank()
        )

        Log.d(TAG, "=== GymAuthFlowTest START uid=$uid ===")

        val r1 = runVariant(uid, pwd, "_appinf")
        val r2 = runVariant(uid, pwd, "_appinf3")

        Log.d(TAG, "=== SUMMARY ===")
        listOf(r1, r2).forEach { r ->
            Log.d(
                TAG,
                "${r.variant}: failedStep=${r.failedStep} status=${r.status} msg=${r.msg} " +
                    "qrCode=${if (r.qrCode != null) "OK(len=${r.qrCode.length})" else "NONE"}"
            )
        }

        when {
            r1.qrCode != null && r2.qrCode == null ->
                Log.d(TAG, "DIAGNOSIS: _appinf works, _appinf3 does not — no endpoint change needed")
            r2.qrCode != null && r1.qrCode == null ->
                Log.e(TAG, "DIAGNOSIS: _appinf3 works but _appinf does not — UPDATE both GymRepository URLs to _appinf3")
            r1.qrCode != null && r2.qrCode != null ->
                Log.d(TAG, "DIAGNOSIS: Both variants work — original failure may be transient")
            else ->
                Log.e(
                    TAG,
                    "DIAGNOSIS: Both variants failed at step '${r1.failedStep}' — " +
                        "check raw JSON above. Possible causes: wrong credentials, server down, or HashCode selector changed"
                )
        }

        assertTrue(
            "Both endpoint variants failed — see Logcat tag $TAG for raw JSON at each step",
            r1.qrCode != null || r2.qrCode != null
        )
    }
}
