package com.example.gymqrdisplayer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gym_prefs")

class DataStoreManager(private val context: Context) {
    companion object {
        val UID_KEY = stringPreferencesKey("uid")
        val PWD_KEY = stringPreferencesKey("pwd")
        val UUID_KEY = stringPreferencesKey("uuid")
    }

    val uidFlow: Flow<String?> = context.dataStore.data.map { it[UID_KEY] }
    val pwdFlow: Flow<String?> = context.dataStore.data.map { it[PWD_KEY] }
    val uuidFlow: Flow<String?> = context.dataStore.data.map { it[UUID_KEY] }

    suspend fun saveCredentials(uid: String, pwd: String) {
        context.dataStore.edit {
            it[UID_KEY] = uid
            it[PWD_KEY] = pwd
        }
    }

    suspend fun saveUuid(uuid: String) {
        context.dataStore.edit {
            it[UUID_KEY] = uuid
        }
    }
}
