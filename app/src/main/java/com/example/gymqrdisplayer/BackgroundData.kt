package com.example.gymqrdisplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * 數據節省模式（Data Saver）開啟時，系統會在計量網路上擋掉 app 的背景網路。
 * 桌面小工具的點擊是在背景執行的，所以會直接 DNS 失敗；前景 Activity 不受影響。
 */
object BackgroundData {

    enum class Status {
        /** 數據節省模式關閉 */
        DATA_SAVER_OFF,
        /** 數據節省模式開啟，但這個 app 已設為「不受限制的數據用量」 */
        EXEMPTED,
        /** 數據節省模式開啟，且這個 app 沒有被除外：背景網路會被擋 */
        RESTRICTED
    }

    fun status(context: Context): Status {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return Status.DATA_SAVER_OFF
        return when (cm.restrictBackgroundStatus) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> Status.EXEMPTED
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> Status.RESTRICTED
            else -> Status.DATA_SAVER_OFF
        }
    }

    fun isRestricted(context: Context): Boolean = status(context) == Status.RESTRICTED

    /** 這個 process 此刻拿不到網路（被系統擋住時 activeNetwork 為 null；真的離線時也是）。 */
    fun isNetworkBlockedNow(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.activeNetwork == null
    }

    /** 開啟這個 app 的數據用量設定頁，使用者可在那裡打開「不受限制的數據用量」。 */
    fun openSettings(context: Context) {
        val pkg = Uri.parse("package:${context.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // 這個裝置沒有該設定頁，試下一個
            }
        }
    }
}

/** 數據節省模式擋住背景網路時顯示的說明，附一顆跳到設定的按鈕；沒被限制時不顯示。 */
@Composable
fun DataSaverNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 從設定頁回來（重新 RESUMED）時重新檢查
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val restricted = remember(lifecycleState) { BackgroundData.isRestricted(context) }
    if (!restricted) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("桌面小工具無法在背景連線", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "手機開著數據節省模式，系統會擋住這個 app 在背景的網路，" +
                    "所以點小工具時只能改開這個畫面。" +
                    "打開「不受限制的數據用量」後，小工具就能直接顯示 QR。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { BackgroundData.openSettings(context) }) {
                Text("前往設定")
            }
        }
    }
}

/**
 * 主畫面常駐的檢查列：
 * 1. 數據節省模式是否開啟
 * 2. 開啟的話，這個 app 是否已被除外（不受限制的數據用量）
 * 被擋時附「前往設定」按鈕。從設定頁回來（重新 RESUMED）時重新檢查。
 */
@Composable
fun BackgroundDataStatusCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val status = remember(lifecycleState) { BackgroundData.status(context) }

    val ok = status != BackgroundData.Status.RESTRICTED
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (ok) CardDefaults.cardColors() else CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("小工具背景連線", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            CheckRow(
                passed = status == BackgroundData.Status.DATA_SAVER_OFF,
                label = "數據節省模式",
                value = if (status == BackgroundData.Status.DATA_SAVER_OFF) "關閉" else "開啟"
            )
            if (status != BackgroundData.Status.DATA_SAVER_OFF) {
                CheckRow(
                    passed = status == BackgroundData.Status.EXEMPTED,
                    label = "本 app 不受限制",
                    value = if (status == BackgroundData.Status.EXEMPTED) "已除外" else "未除外"
                )
            }
            if (!ok) {
                Text(
                    "背景網路會被系統擋住，點桌面小工具時只能改開 QR 畫面。" +
                        "打開「不受限制的數據用量」後，小工具就能直接顯示 QR。",
                    style = MaterialTheme.typography.bodySmall
                )
                FilledTonalButton(onClick = { BackgroundData.openSettings(context) }) {
                    Text("前往設定")
                }
            }
        }
    }
}

@Composable
private fun CheckRow(passed: Boolean, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (passed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
