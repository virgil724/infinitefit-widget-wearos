package com.example.gymqrdisplayer.wear

import android.graphics.Bitmap
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

@Composable
fun QrScreen(viewModel: WearViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is WearViewModel.UiState.Loading -> {
                    CircularProgressIndicator()
                }
                is WearViewModel.UiState.Success -> {
                    QrCodeDisplay(
                        bitmap = state.qrBitmap,
                        updatedAt = state.updatedAt,
                        onRefresh = { viewModel.loadQrCode() }
                    )
                }
                is WearViewModel.UiState.Error -> {
                    ErrorDisplay(
                        message = state.message,
                        onRetry = { viewModel.loadQrCode() }
                    )
                }
                is WearViewModel.UiState.NoCredentials -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "請先設定帳號",
                            style = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCodeDisplay(
    bitmap: Bitmap,
    updatedAt: String,
    onRefresh: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onRefresh() },
        contentAlignment = Alignment.Center
    ) {
        // 圓形螢幕：最大內接正方形 ≈ 直徑 × 0.65，留出四角截切空間
        val qrSize = minOf(maxWidth, maxHeight) * 0.65f

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(qrSize)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "健身房 QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = updatedAt,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ErrorDisplay(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        CompactChip(
            onClick = onRetry,
            label = { Text("重試") }
        )
    }
}
