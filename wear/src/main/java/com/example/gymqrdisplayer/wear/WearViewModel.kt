package com.example.gymqrdisplayer.wear

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WearViewModel(application: Application) : AndroidViewModel(application) {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val qrBitmap: Bitmap, val updatedAt: String) : UiState()
        data class Error(val message: String) : UiState()
        object NoCredentials : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val repository = GymRepository.instance
    private val dataStoreManager = DataStoreManager(application)

    private var loadingJob: Job? = null

    // UUID 快取（15 分鐘 TTL，避免重複登入）
    private val uuidMutex = Mutex()
    private var cachedUuid: String? = null
    private var uuidCachedAt: Long = 0L
    private val UUID_TTL_MS = 15 * 60 * 1000L

    init {
        // 觀察 uid 的變化（包含 WearCredentialSyncService 寫入新憑證後的通知）
        viewModelScope.launch {
            dataStoreManager.uidFlow
                .distinctUntilChanged()
                .collect { uid ->
                    if (!uid.isNullOrBlank()) {
                        Log.d("WearVM", "UID updated to $uid, clearing UUID cache")
                        cachedUuid = null
                        uuidCachedAt = 0L
                        if (loadingJob?.isActive != true) {
                            loadQrCode()
                        }
                    } else {
                        _uiState.value = UiState.NoCredentials
                    }
                }
        }
    }

    fun loadQrCode() {
        if (loadingJob?.isActive == true) return

        loadingJob = viewModelScope.launch {
            _uiState.value = UiState.Loading

            val uid = dataStoreManager.uidFlow.first()
            val pwd = dataStoreManager.getPassword()

            if (uid.isNullOrBlank() || pwd.isNullOrBlank()) {
                _uiState.value = UiState.NoCredentials
                return@launch
            }

            try {
                val (uuid, authError) = uuidMutex.withLock {
                    val now = System.currentTimeMillis()
                    if (cachedUuid != null && (now - uuidCachedAt) < UUID_TTL_MS) {
                        Log.d("WearVM", "Using cached UUID")
                        Pair(cachedUuid, null)
                    } else {
                        val hashCode = repository.getHashCode()
                            ?: return@withLock Pair(null, "無法取得伺服器回應，請確認網路連線")
                        val newUuid = repository.login(uid, pwd, hashCode)
                            ?: return@withLock Pair(null, "登入失敗，請確認帳號密碼")
                        cachedUuid = newUuid
                        uuidCachedAt = now
                        Pair(newUuid, null)
                    }
                }
                if (uuid == null) {
                    _uiState.value = UiState.Error(authError ?: "認證失敗")
                    return@launch
                }

                val qrContent = repository.generateQRCode(uuid)
                if (qrContent == null) {
                    cachedUuid = null // 讓下次重新登入
                    _uiState.value = UiState.Error("QR Code 生成失敗，請重試")
                    return@launch
                }

                // 儲存 QR 內容供 Tile 使用
                dataStoreManager.saveQrContent(qrContent)

                val bitmap = repository.createQRCodeBitmap(qrContent)
                val updatedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _uiState.value = UiState.Success(bitmap, updatedAt)

            } catch (e: Exception) {
                Log.e("WearVM", "Error loading QR", e)
                _uiState.value = UiState.Error("網路錯誤，請重試")
            }
        }
    }

    fun saveCredentialsAndLoad(uid: String, pwd: String) {
        loadingJob?.cancel()
        cachedUuid = null
        viewModelScope.launch {
            dataStoreManager.saveCredentials(uid, pwd)
            // uidFlow observer 會自動觸發 loadQrCode()
        }
    }

    fun onSyncComplete() {
        Log.d("WearVM", "Sync complete broadcast received, forcing reload.")
        loadingJob?.cancel()
        cachedUuid = null
        loadQrCode()
    }

    fun requestCredentialsFromPhone() {
        viewModelScope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(getApplication<Application>())
                val nodes = withTimeout(5000L) { nodeClient.connectedNodes.await() }
                if (nodes.isEmpty()) {
                    _uiState.value = UiState.Error("未連接到手機，請確認手機在範圍內")
                    return@launch
                }
                val messageClient = Wearable.getMessageClient(getApplication<Application>())
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, "/request_credentials", ByteArray(0)).await()
                    Log.d("WearVM", "Credentials requested from ${node.displayName}")
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.value = UiState.Error("手機連線逾時，請確認手機在範圍內")
            } catch (e: Exception) {
                Log.e("WearVM", "Error requesting credentials", e)
                _uiState.value = UiState.Error("無法連接手機，請稍後再試")
            }
        }
    }

    fun clearCachedUuid() {
        cachedUuid = null
    }
}
