package com.example.gymqrdisplayer.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wear_gym_prefs")

class DataStoreManager(private val context: Context) {
    companion object {
        val UID_KEY = stringPreferencesKey("uid")
        val UUID_KEY = stringPreferencesKey("uuid")
        val QR_CONTENT_KEY = stringPreferencesKey("qr_content")

        private const val ENCRYPTED_PREFS_NAME = "wear_gym_encrypted_prefs"
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
    val qrContentFlow: Flow<String?> = context.dataStore.data.map { it[QR_CONTENT_KEY] }

    suspend fun saveCredentials(uid: String, pwd: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 先存密碼（不觸發 Flow），確保 uidFlow 發出時密碼已可讀
        encryptedPrefs.edit().apply {
            putString("pwd_encrypted", pwd)
            commit()
        }
        context.dataStore.edit { it[UID_KEY] = uid }
    }

    suspend fun getPassword(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext encryptedPrefs.getString("pwd_encrypted", null)
    }

    suspend fun saveQrContent(content: String) {
        context.dataStore.edit { it[QR_CONTENT_KEY] = content }
    }
}
