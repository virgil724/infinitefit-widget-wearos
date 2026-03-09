package com.example.gymqrdisplayer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gym_prefs")

class DataStoreManager(private val context: Context) {
    companion object {
        val UID_KEY = stringPreferencesKey("uid")
        val PWD_KEY = stringPreferencesKey("pwd")
        val UUID_KEY = stringPreferencesKey("uuid")

        private const val ENCRYPTED_PREFS_NAME = "gym_encrypted_prefs"
    }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val uidFlow: Flow<String?> = context.dataStore.data.map { it[UID_KEY] }
    val pwdFlow: Flow<String?> = context.dataStore.data.map { it[PWD_KEY] }
    val uuidFlow: Flow<String?> = context.dataStore.data.map { it[UUID_KEY] }

    suspend fun saveCredentials(uid: String, pwd: String) {
        // Store UID in regular DataStore (non-sensitive)
        context.dataStore.edit {
            it[UID_KEY] = uid
        }
        // Store password in encrypted SharedPreferences
        encryptedPrefs.edit().apply {
            putString("pwd_encrypted", pwd)
            apply()
        }
    }

    suspend fun getPassword(): String? {
        return encryptedPrefs.getString("pwd_encrypted", null)
    }

    suspend fun saveUuid(uuid: String) {
        context.dataStore.edit {
            it[UUID_KEY] = uuid
        }
    }
}
