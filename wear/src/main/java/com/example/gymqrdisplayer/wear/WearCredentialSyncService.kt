package com.example.gymqrdisplayer.wear

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WearCredentialSyncService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                if (path == "/gym_credentials") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val uid = dataMap.getString("uid") ?: return@forEach
                    val pwd = dataMap.getString("pwd") ?: return@forEach

                    serviceScope.launch {
                        DataStoreManager(applicationContext).saveCredentials(uid, pwd)
                        Log.d("WearSync", "Credentials synced from phone for uid: $uid")
                        
                        // Notify UI that sync is complete
                        val intent = Intent("com.example.gymqrdisplayer.SYNC_COMPLETE")
                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
