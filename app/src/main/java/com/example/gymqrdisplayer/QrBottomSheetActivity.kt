package com.example.gymqrdisplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.gymqrdisplayer.ui.theme.GYMQRDisplayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QrBottomSheetActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GYMQRDisplayerTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var showSheet by remember { mutableStateOf(true) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(showSheet) {
                    if (showSheet) {
                        sheetState.expand()
                        delay(120)
                        sheetState.expand()
                    }
                }

                if (showSheet) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            showSheet = false
                            finish()
                        },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surface,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        QrContent(onRetry = {
                            scope.launch {
                                showSheet = false
                                delay(100)
                                showSheet = true
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun QrContent(onRetry: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { GymRepository.instance }
    val dataStore = remember { DataStoreManager(context) }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val placeholderBoxSize = 230.dp

    fun loadQrCode() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val uid = dataStore.uidFlow.first()
                val pwd = dataStore.getPassword()

                if (uid != null && pwd != null) {
                    val hashCode = repository.getHashCode()
                    if (hashCode != null) {
                        val uuid = repository.login(uid, pwd, hashCode)
                        if (uuid != null) {
                            val qrCode = repository.generateQRCode(uuid)
                            if (qrCode != null) {
                                qrBitmap = repository.createQRCodeBitmap(qrCode)
                            } else {
                                errorMsg = "Failed to generate QR"
                            }
                        } else {
                            errorMsg = "Login failed - check credentials"
                        }
                    } else {
                        errorMsg = "Connection error"
                    }
                } else {
                    errorMsg = "Credentials not set"
                }
            } catch (e: Exception) {
                errorMsg = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadQrCode()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your QR Code",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Show this to the front desk to check in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(placeholderBoxSize),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        Text("Generating QR Code...")
                    }
                }
            }
        } else if (errorMsg != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    errorMsg!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
                Button(onClick = {
                    context.let { ctx -> (ctx as? ComponentActivity)?.finish() }
                }) {
                    Text("Close")
                }
            }
        } else if (qrBitmap != null) {
            Surface(
                modifier = Modifier
                    .size(placeholderBoxSize)
                    .padding(6.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Image(
                    bitmap = qrBitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize().padding(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Scan to Enter", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tip: Keep your screen brightness high for faster scanning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
