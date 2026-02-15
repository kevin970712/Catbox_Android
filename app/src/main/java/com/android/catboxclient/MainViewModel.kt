package com.android.catboxclient

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    var uploadState by mutableStateOf<UploadState>(UploadState.Idle)
        private set

    var selectedService by mutableStateOf(ServiceType.CATBOX)
    var litterboxTime by mutableStateOf("1h")
    var userHash by mutableStateOf("")

    fun loadUserHash(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("catbox_prefs", Context.MODE_PRIVATE)
        userHash = prefs.getString("user_hash", "") ?: ""
    }

    fun updateUserHash(context: Context, newHash: String) {
        val appContext = context.applicationContext
        userHash = newHash
        val prefs = appContext.getSharedPreferences("catbox_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_hash", newHash).apply()
    }

    fun uploadFile(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            uploadState = UploadState.Loading(0f)

            try {
                val resultUrl = withContext(Dispatchers.IO) {
                    NativeUploader.upload(
                        context = appContext,
                        uri = uri,
                        serviceType = selectedService,
                        time = litterboxTime,
                        userHash = userHash,
                        onProgress = { progress ->
                            uploadState = UploadState.Loading(progress)
                        }
                    )
                }

                val bitmap = withContext(Dispatchers.Default) {
                    QrCodeGenerator.generate(resultUrl, size = 300)
                }

                uploadState = UploadState.Success(resultUrl, bitmap)

            } catch (e: Exception) {
                e.printStackTrace()
                uploadState = UploadState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    fun resetState() {
        uploadState = UploadState.Idle
    }
}

sealed class UploadState {
    object Idle : UploadState()
    data class Loading(val progress: Float) : UploadState()
    data class Success(val url: String, val qrBitmap: Bitmap) : UploadState()
    data class Error(val message: String) : UploadState()
}

enum class ServiceType { CATBOX, LITTERBOX }