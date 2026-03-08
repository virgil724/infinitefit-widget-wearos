package com.example.gymqrdisplayer

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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

    companion object {
        val QR_CODE_KEY = stringPreferencesKey("qr_code_content")
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val qrContent = prefs[QR_CODE_KEY]
                WidgetContent(qrContent)
            }
        }
    }

    @Composable
    private fun WidgetContent(qrContent: String?) {
        // 整個小工具背景都設為可點擊，點擊即觸發刷新
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center
        ) {
            if (!qrContent.isNullOrEmpty()) {
                val bitmap = GymRepository().createQRCodeBitmap(qrContent)
                // QR Code 容器：保持白色背景以利掃描
                Box(
                    modifier = GlanceModifier
                        .padding(12.dp)
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
                
                // 右下角的小提示
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(8.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Text(
                        text = "Tap to Refresh",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
            } else {
                // 尚未生成狀態
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap to",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp)
                    )
                    Text(
                        text = "Generate",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 16.sp)
                    )
                }
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d("GymWidget", "RefreshAction triggered via Tap")
        val repository = GymRepository()
        val dataStore = DataStoreManager(context)
        val uid = dataStore.uidFlow.first()
        val pwd = dataStore.pwdFlow.first()
        
        if (uid != null && pwd != null) {
            val hashCode = repository.getHashCode()
            if (hashCode != null) {
                val uuid = repository.login(uid, pwd, hashCode)
                if (uuid != null) {
                    val qrCode = repository.generateQRCode(uuid)
                    if (qrCode != null) {
                        updateAppWidgetState(context, glanceId) { prefs ->
                            prefs[GymWidget.QR_CODE_KEY] = qrCode
                        }
                        GymWidget().update(context, glanceId)
                        return
                    }
                }
            }
        }
        Log.e("GymWidget", "RefreshAction failed")
    }
}

class GymWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GymWidget()
}
