package com.example.gymqrdisplayer

import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymqrdisplayer.ui.theme.GYMQRDisplayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val QR_VALID_SEC = 28

class QrBottomSheetActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 出示 QR 時把亮度拉到最高，掃描更快
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }

        setContent {
            GYMQRDisplayerTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                LaunchedEffect(Unit) {
                    sheetState.expand()
                    delay(120)
                    sheetState.expand()
                }
                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    QrContent(onClose = { finish() })
                }
            }
        }
    }
}

@Composable
fun QrContent(onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { GymRepository.instance }
    val dataStore = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generatedAt by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableIntStateOf(QR_VALID_SEC) }
    val cardSize = 264.dp

    fun loadQrCode() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val uid = dataStore.uidFlow.first()
                val pwd = dataStore.getPassword()
                if (uid.isNullOrBlank() || pwd.isNullOrBlank()) {
                    errorMsg = "尚未設定帳號"
                    return@launch
                }
                val qrCode = repository.generateQrCodeForUser(uid, pwd)
                if (qrCode != null) {
                    qrBitmap = repository.createQRCodeBitmap(qrCode)
                    generatedAt = System.currentTimeMillis()
                    remaining = QR_VALID_SEC
                } else {
                    GymRepository.invalidateUuid()
                    errorMsg = "QR Code 產生失敗"
                }
            } catch (e: Exception) {
                errorMsg = e.message ?: "未知錯誤"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadQrCode() }

    // 倒數；到期自動重新產生（只在畫面前景時，避免背景一直打 API）
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(generatedAt, resumed) {
        if (generatedAt == 0L) return@LaunchedEffect
        while (true) {
            val left = QR_VALID_SEC - ((System.currentTimeMillis() - generatedAt) / 1000).toInt()
            remaining = left.coerceAtLeast(0)
            if (left <= 0) {
                if (resumed) loadQrCode()
                break
            }
            delay(250)
        }
    }

    val accent by animateColorAsState(
        when {
            remaining <= 6 -> MaterialTheme.colorScheme.error
            remaining <= 12 -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "accent"
    )
    val progress by animateFloatAsState(
        remaining / QR_VALID_SEC.toFloat(),
        animationSpec = tween(400),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("入場 QR Code", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "請將 QR 對準櫃台掃描器",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        val err = errorMsg
        when {
            err != null && qrBitmap == null -> {
                Surface(
                    modifier = Modifier.size(cardSize),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            err,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { loadQrCode() }) { Text("重試") }
                    }
                }
            }

            else -> {
                // 白色卡片 + QR；載入中半透明並疊上轉圈
                Box(
                    modifier = Modifier
                        .size(cardSize)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(Color.White) // QR 必須白底黑碼才好掃
                        .clickable(enabled = !isLoading) { loadQrCode() },
                    contentAlignment = Alignment.Center
                ) {
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                                .alpha(if (isLoading) 0.15f else 1f)
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 倒數條（到期自動重新產生）
                Column(Modifier.width(cardSize)) {
                    LinearProgressIndicator(
                        progress = { if (isLoading) 0f else progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onClose) { Text("關閉") }
                    FilledTonalButton(onClick = { loadQrCode() }, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新產生")
                    }
                }
            }
        }
    }
}
