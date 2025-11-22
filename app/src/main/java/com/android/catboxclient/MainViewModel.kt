package com.android.catboxclient

import android.content.Context
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

    fun uploadFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            uploadState = UploadState.Loading
            try {
                val resultUrl = withContext(Dispatchers.IO) {
                    NativeUploader.upload(context, uri, selectedService, litterboxTime)
                }
                uploadState = UploadState.Success(resultUrl)
            } catch (e: Exception) {
                e.printStackTrace()
                // 翻譯未知錯誤訊息
                uploadState = UploadState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }
}

sealed class UploadState {
    object Idle : UploadState()
    object Loading : UploadState()
    data class Success(val url: String) : UploadState()
    data class Error(val message: String) : UploadState()
}

enum class ServiceType { CATBOX, LITTERBOX }