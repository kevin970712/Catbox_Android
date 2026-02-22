package com.android.catboxclient

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val repository: PreferenceRepository) : ViewModel() {
    var uploadState by mutableStateOf<UploadState>(UploadState.Idle)
        private set

    var selectedService by mutableStateOf(ServiceType.CATBOX)
    var litterboxTime by mutableStateOf("1h")
    var userHash by mutableStateOf(repository.getUserHash())
        private set

    init {
        viewModelScope.launch {
            repository.userHashFlow.collect { newHash ->
                userHash = newHash
            }
        }
    }

    fun uploadFile(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            uploadState = UploadState.Loading(0f)

            try {
                val resultUrl = withContext(Dispatchers.IO) {
                    NativeUploader.upload(
                        contentResolver = contentResolver,
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

    class Factory(private val repository: PreferenceRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed class UploadState {
    object Idle : UploadState()
    data class Loading(val progress: Float) : UploadState()
    data class Success(val url: String, val qrBitmap: Bitmap) : UploadState()
    data class Error(val message: String) : UploadState()
}

enum class ServiceType { CATBOX, LITTERBOX }