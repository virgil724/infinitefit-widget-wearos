package com.example.gymqrdisplayer

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearDataLayerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/request_credentials") {
            serviceScope.launch {
                try {
                    val dataStore = DataStoreManager(applicationContext)
                    val uid = dataStore.uidFlow.first() ?: run {
                        Log.w("WearDataLayer", "No UID stored on phone")
                        return@launch
                    }
                    val pwd = dataStore.getPassword() ?: run {
                        Log.w("WearDataLayer", "No password stored on phone")
                        return@launch
                    }

                    val request = PutDataMapRequest.create("/gym_credentials").apply {
                        dataMap.putString("uid", uid)
                        dataMap.putString("pwd", pwd)
                        dataMap.putLong("timestamp", System.currentTimeMillis())
                    }.asPutDataRequest().setUrgent()

                    val dataClient = Wearable.getDataClient(applicationContext)
                    val dataItem = dataClient.putDataItem(request).await()
                    Log.d("WearDataLayer", "Credentials sent to watch")

                    // 10 秒後刪除 DataItem，避免敏感資料長期存在 Data Layer
                    delay(10_000)
                    dataClient.deleteDataItems(dataItem.uri).await()
                    Log.d("WearDataLayer", "Credentials DataItem deleted")

                } catch (e: Exception) {
                    Log.e("WearDataLayer", "Error sending credentials to watch", e)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
