package com.example.gymqrdisplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.gymqrdisplayer.ui.theme.GYMQRDisplayerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataStoreManager = DataStoreManager(this)

        enableEdgeToEdge()
        setContent {
            GYMQRDisplayerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { LargeTopAppBar(title = { Text("GYM QR") }) }
                ) { innerPadding ->
                    LoginScreen(
                        dataStoreManager = dataStoreManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(dataStoreManager: DataStoreManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val savedUid by dataStoreManager.uidFlow.collectAsState(initial = null)
    var uid by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        uid = dataStoreManager.uidFlow.first() ?: ""
        pwd = dataStoreManager.getPassword() ?: ""
    }

    val signedIn = !savedUid.isNullOrBlank()
    val showForm = !signedIn || editing

    fun submit() {
        if (uid.isBlank() || pwd.isBlank()) {
            loginError = "請輸入手機號碼與密碼"
            return
        }
        isLoggingIn = true
        loginError = null
        scope.launch {
            try {
                val repository = GymRepository.instance
                val hashCode = repository.getHashCode()
                if (hashCode == null) {
                    loginError = "連線失敗，請稍後再試"
                    return@launch
                }
                val uuid = repository.login(uid, pwd, hashCode)
                if (uuid != null) {
                    GymRepository.cacheUuid(uuid)
                    dataStoreManager.saveCredentials(uid, pwd)
                    editing = false
                } else {
                    loginError = "手機號碼或密碼不正確"
                }
            } catch (e: Exception) {
                loginError = "錯誤：${e.message}"
            } finally {
                isLoggingIn = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Primary action
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (signedIn) "出示入場 QR" else "先登入健身房帳號",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "QR 有效約 30 秒，過期會自動重新產生",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { context.startActivity(Intent(context, QrBottomSheetActivity::class.java)) },
                    enabled = signedIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("顯示 QR Code")
                }
            }
        }

        // ---- Account
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "帳號",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (signedIn && !editing) {
                        TextButton(onClick = { editing = true }) { Text("變更") }
                    }
                }

                if (signedIn && !editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(maskPhone(savedUid.orEmpty()), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "密碼已加密儲存在此裝置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showForm) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uid,
                            onValueChange = { v -> uid = v.filter { it.isDigit() || it == '#' } },
                            label = { Text("手機號碼") },
                            leadingIcon = { Icon(Icons.Filled.Phone, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pwd,
                            onValueChange = { pwd = it },
                            label = { Text("密碼") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        loginError?.let { err ->
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    err,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (signedIn) {
                                TextButton(
                                    onClick = { editing = false; loginError = null }
                                ) { Text("取消") }
                            }
                            FilledTonalButton(
                                onClick = { submit() },
                                enabled = !isLoggingIn,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isLoggingIn) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("登入中…")
                                } else {
                                    Text("登入並儲存")
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Shortcuts guide
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("更快出示", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                GuideRow("1", "桌面小工具", "長按桌面 → 小工具 → GYM QR，一點就出現 QR")
                GuideRow("2", "手錶資訊方塊", "手錶長按錶面 → 新增資訊方塊 → GYM QR")
                GuideRow("3", "手錶同步", "手錶 App 內點「從手機同步」即可帶入帳號")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GuideRow(index: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                index,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun maskPhone(uid: String): String =
    if (uid.length >= 7) uid.take(4) + "•".repeat(uid.length - 7) + uid.takeLast(3) else uid
