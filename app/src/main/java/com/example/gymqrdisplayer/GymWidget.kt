package com.example.gymqrdisplayer

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.LinearProgressIndicator
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.min
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.GlanceTheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first

class GymWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    companion object {
        val QR_CODE_KEY = stringPreferencesKey("qr_code_content")
        val IS_LOADING_KEY = booleanPreferencesKey("is_loading")
        val GENERATED_AT_KEY = longPreferencesKey("generated_at")
        const val QR_VALID_SEC = 28
        val ERROR_MSG_KEY = stringPreferencesKey("error_msg")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val qrContent = prefs[QR_CODE_KEY]
                val isLoading = prefs[IS_LOADING_KEY] ?: false
                val generatedAt = prefs[GENERATED_AT_KEY] ?: 0L
                val errorMsg = prefs[ERROR_MSG_KEY]

                // 倒數：Glance session 存活期間每秒重組一次（純本地，不打 API）
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(generatedAt) {
                    now = System.currentTimeMillis()
                    while (generatedAt > 0 && now - generatedAt < QR_VALID_SEC * 1000L) {
                        delay(1000)
                        now = System.currentTimeMillis()
                    }
                }
                val remaining = if (generatedAt == 0L) 0
                    else (QR_VALID_SEC - ((now - generatedAt) / 1000).toInt()).coerceAtLeast(0)

                WidgetContent(qrContent, isLoading, remaining, errorMsg)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        qrContent: String?,
        isLoading: Boolean,
        remaining: Int,
        errorMsg: String?
    ) {
        val size = LocalSize.current
        val qrSize = min(size.width - 32.dp, size.height - 40.dp).coerceAtLeast(80.dp)

        val baseModifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)

        Box(
            modifier = baseModifier.clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "更新中...",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 16.sp
                        )
                    )
                }
            } else if (!errorMsg.isNullOrEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "更新失敗",
                        style = TextStyle(
                            color = GlanceTheme.colors.error,
                            fontSize = 16.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = errorMsg,
                        style = TextStyle(
                            color = GlanceTheme.colors.onErrorContainer,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(
                        text = "點擊重試",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }
            } else if (!qrContent.isNullOrEmpty() && remaining > 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bitmap = GymRepository.instance.createQRCodeBitmap(qrContent)
                    // QR Code container: keep white background for scan reliability
                    Box(
                        modifier = GlanceModifier
                            .size(qrSize) // Ensure it fits with text below
                            .background(Color.White)
                            .cornerRadius(8.dp)
                            .padding(8.dp)
                    ) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = "QR Code",
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = remaining / QR_VALID_SEC.toFloat(),
                        modifier = GlanceModifier.width(qrSize).height(6.dp).cornerRadius(3.dp),
                        color = when {
                            remaining <= 6 -> GlanceTheme.colors.error
                            remaining <= 12 -> GlanceTheme.colors.tertiary
                            else -> GlanceTheme.colors.primary
                        },
                        backgroundColor = GlanceTheme.colors.surfaceVariant
                    )
                }
            } else if (!qrContent.isNullOrEmpty()) {
                // 已過期：不顯示舊 QR，避免掃到失效的
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已過期",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "點擊重新產生",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GYM QR",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "點擊產生 QR Code",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp)
                    )
                }
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d("GymWidget", "RefreshAction triggered via Tap")
        val repository = GymRepository.instance
        val dataStore = DataStoreManager(context)

        // Clear existing states and set loading
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[GymWidget.IS_LOADING_KEY] = true
            prefs.remove(GymWidget.QR_CODE_KEY)
            prefs.remove(GymWidget.GENERATED_AT_KEY)
            prefs.remove(GymWidget.ERROR_MSG_KEY)
        }
        GymWidget().update(context, glanceId)

        var errorMessage: String? = null

        try {
            val uid = dataStore.uidFlow.first()
            val pwd = dataStore.getPassword()

            if (uid.isNullOrBlank() || pwd.isNullOrBlank()) {
                errorMessage = "尚未設定帳號密碼"
            } else {
                val qrCode = repository.generateQrCodeForUser(uid, pwd)
                if (qrCode != null) {
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[GymWidget.QR_CODE_KEY] = qrCode
                        prefs[GymWidget.GENERATED_AT_KEY] = System.currentTimeMillis()
                    }
                } else {
                    GymRepository.invalidateUuid()
                    errorMessage = "無法取得 QR Code，請重試"
                }
            }
        } catch (e: SecurityException) {
            Log.e("GymWidget", "SecurityException in RefreshAction", e)
            errorMessage = "權限或金鑰錯誤: ${e.javaClass.simpleName} - ${e.message}"
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("GymWidget", "Timeout in RefreshAction", e)
            errorMessage = "網路連線逾時，可能被系統限制"
        } catch (e: java.net.UnknownHostException) {
            Log.e("GymWidget", "UnknownHostException in RefreshAction", e)
            errorMessage = "網路斷線或無法解析網域"
        } catch (e: Exception) {
            Log.e("GymWidget", "RefreshAction failed", e)
            errorMessage = "${e.javaClass.simpleName}: ${e.message ?: "發生未知錯誤"}"
        } finally {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[GymWidget.IS_LOADING_KEY] = false
                if (errorMessage != null) {
                    prefs[GymWidget.ERROR_MSG_KEY] = errorMessage!!
                }
            }
            GymWidget().update(context, glanceId)
        }
    }
}

class GymWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GymWidget()
}
