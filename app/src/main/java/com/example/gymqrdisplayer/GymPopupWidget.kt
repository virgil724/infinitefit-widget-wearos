package com.example.gymqrdisplayer

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
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
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.GlanceTheme
import androidx.glance.text.FontWeight

class GymPopupWidget : GlanceAppWidget() {

    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier
                    .width(150.dp)
                    .height(56.dp)
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(28.dp)
                    .clickable(actionRunCallback<LaunchPopupAction>())
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .background(GlanceTheme.colors.primary)
                        .cornerRadius(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_qr_pattern),
                        contentDescription = "QR Code Icon",
                        modifier = GlanceModifier.size(24.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary)
                    )
                }

                Spacer(modifier = GlanceModifier.width(12.dp))

                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GYM",
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Quick Check-in",
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(12.dp))
            }
        }
    }
}

class LaunchPopupAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, QrBottomSheetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

class GymPopupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GymPopupWidget()
}
