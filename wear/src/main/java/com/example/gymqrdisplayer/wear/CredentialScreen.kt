package com.example.gymqrdisplayer.wear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.app.RemoteInput
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.input.RemoteInputIntentHelper

@Composable
fun CredentialScreen(
    viewModel: WearViewModel,
    onCredentialsSaved: () -> Unit
) {
    val context = LocalContext.current
    var uid by rememberSaveable { mutableStateOf("") }
    var pwd by rememberSaveable { mutableStateOf("") }

    // Register broadcast receiver for sync completion
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.gymqrdisplayer.SYNC_COMPLETE") {
                    viewModel.onSyncComplete()
                    onCredentialsSaved() // This navigates back
                }
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter("com.example.gymqrdisplayer.SYNC_COMPLETE")
        )
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

    // UID 輸入啟動器
    val uidLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val bundle = RemoteInput.getResultsFromIntent(data)
                uid = bundle?.getCharSequence("uid")?.toString() ?: uid
            }
        }
    }

    // 密碼輸入啟動器
    val pwdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val bundle = RemoteInput.getResultsFromIntent(data)
                pwd = bundle?.getCharSequence("pwd")?.toString() ?: pwd
            }
        }
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    "設定帳號",
                    style = MaterialTheme.typography.title3
                )
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // UID 輸入
            item {
                Chip(
                    onClick = {
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        if (intent != null) {
                            val remoteInputs = listOf(
                                RemoteInput.Builder("uid")
                                    .setLabel("UID 帳號")
                                    .build()
                            )
                            RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
                            uidLauncher.launch(intent)
                        }
                    },
                    label = { Text(if (uid.isBlank()) "輸入帳號" else uid) },
                    secondaryLabel = { Text("UID") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 密碼輸入
            item {
                Chip(
                    onClick = {
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        if (intent != null) {
                            val remoteInputs = listOf(
                                RemoteInput.Builder("pwd")
                                    .setLabel("密碼")
                                    .build()
                            )
                            RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
                            pwdLauncher.launch(intent)
                        }
                    },
                    label = { Text(if (pwd.isBlank()) "輸入密碼" else "•".repeat(pwd.length.coerceAtMost(8))) },
                    secondaryLabel = { Text("Password") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 儲存按鈕
            item {
                Button(
                    onClick = {
                        if (uid.isNotBlank() && pwd.isNotBlank()) {
                            viewModel.saveCredentialsAndLoad(uid, pwd)
                            onCredentialsSaved()
                        }
                    },
                    enabled = uid.isNotBlank() && pwd.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text("儲存並登入")
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 從手機同步
            item {
                CompactChip(
                    onClick = { viewModel.requestCredentialsFromPhone() },
                    label = { Text("從手機同步") }
                )
            }
        }
    }
}
