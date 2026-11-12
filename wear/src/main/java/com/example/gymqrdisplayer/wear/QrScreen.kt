package com.example.gymqrdisplayer.wear

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

private const val QR_VALID_SEC = 28

@Composable
fun QrScreen(viewModel: WearViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    KeepBright()

    Scaffold {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is WearViewModel.UiState.Loading -> LoadingDisplay()
                is WearViewModel.UiState.Success -> QrCodeDisplay(
                    bitmap = state.qrBitmap,
                    updatedAt = state.updatedAt,
                    generatedAtMillis = state.generatedAtMillis,
                    onRefresh = { viewModel.loadQrCode() }
                )
                is WearViewModel.UiState.Error -> MessageDisplay(
                    title = "出錯了",
                    message = state.message,
                    chip = "重試",
                    onClick = { viewModel.loadQrCode() }
                )
                is WearViewModel.UiState.NoCredentials -> MessageDisplay(
                    title = "尚未登入",
                    message = "請先設定帳號",
                    chip = null,
                    onClick = {}
                )
            }
        }
    }
}

/** 顯示 QR 時把手錶亮度拉到最高，離開時還原 */
@Composable
private fun KeepBright() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val original = window.attributes.screenBrightness
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = original }
        }
    }
}

/** 外圈倒數：與 Tile 相同，從頂端往兩側對稱縮短（收在底部） */
@Composable
private fun Ring(progress: Float, color: Color) {
    val track = MaterialTheme.colors.surface
    Canvas(Modifier.fillMaxSize().padding(5.dp)) {
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        drawArc(track, 0f, 360f, useCenter = false, style = Stroke(width = 5.dp.toPx()))
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0.5f) drawArc(color, 90f - sweep / 2f, sweep, useCenter = false, style = stroke)
    }
}

@Composable
private fun QrCard(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White) // QR 必須白底黑碼才好掃
            .clickable { onClick() }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun LoadingDisplay() {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun QrCodeDisplay(
    bitmap: Bitmap,
    updatedAt: String,
    generatedAtMillis: Long,
    onRefresh: () -> Unit
) {
    var remaining by remember(generatedAtMillis) { mutableIntStateOf(QR_VALID_SEC) }

    // 倒數；到期自動重新產生（只在畫面前景時，避免背景一直打 API）
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(generatedAtMillis, resumed) {
        while (true) {
            val left = QR_VALID_SEC - ((System.currentTimeMillis() - generatedAtMillis) / 1000).toInt()
            remaining = left.coerceAtLeast(0)
            if (left <= 0) {
                if (resumed) onRefresh()
                break
            }
            delay(250)
        }
    }

    val accent by animateColorAsState(
        when {
            remaining <= 6 -> MaterialTheme.colors.error
            remaining <= 12 -> MaterialTheme.colors.secondary
            else -> MaterialTheme.colors.primary
        },
        label = "accent"
    )
    val progress by animateFloatAsState(
        remaining / QR_VALID_SEC.toFloat(),
        animationSpec = tween(400),
        label = "progress"
    )

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cardSize = minOf(maxWidth, maxHeight) * 0.6f
        Ring(progress = progress, color = accent)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 上方留出與底部狀態列等高的空間，讓 QR 置中
            Text(" ", style = MaterialTheme.typography.button)
            Spacer(Modifier.height(4.dp))
            QrCard(cardSize, onRefresh) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "健身房 QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(5.dp))
            // 倒數期間留白（外圈就是指示），過期才顯示
            Text(
                if (remaining > 0) " " else "已過期",
                style = MaterialTheme.typography.button,
                color = MaterialTheme.colors.error
            )
        }
    }
}

@Composable
private fun MessageDisplay(title: String, message: String, chip: String?, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.title2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            Text(message, style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
            if (chip != null) {
                Spacer(Modifier.height(12.dp))
                CompactChip(onClick = onClick, label = { Text(chip) })
            }
        }
    }
}
