package com.example.gymqrdisplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.gymqrdisplayer.ui.theme.GYMQRDisplayerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QrBottomSheetActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            GYMQRDisplayerTheme {
                val sheetState = rememberModalBottomSheetState()
                var showSheet by remember { mutableStateOf(true) }
                val scope = rememberCoroutineScope()
                
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
                        QrContent()
                    }
                }
            }
        }
    }
}

@Composable
fun QrContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { GymRepository() }
    val dataStore = remember { DataStoreManager(context) }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val uid = dataStore.uidFlow.first()
                val pwd = dataStore.pwdFlow.first()
                
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
                            errorMsg = "Login failed"
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            Text("Generating QR Code...")
        } else if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* retry logic could go here */ }) {
                Text("Close")
            }
        } else if (qrBitmap != null) {
            Surface(
                modifier = Modifier
                    .size(220.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Image(
                    bitmap = qrBitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Scan to Enter", style = MaterialTheme.typography.titleMedium)
        }
    }
}
