package com.android.catboxclient

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object NativeUploader {

    fun upload(
        context: Context,
        uri: Uri,
        serviceType: ServiceType,
        time: String,
        userHash: String?,
        onProgress: (Float) -> Unit
    ): String {
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "tmp"
        val safeFileName = "upload_${System.currentTimeMillis()}.$extension"

        val totalFileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        } ?: 0L

        val boundary = "--------AliucordBoundary${UUID.randomUUID().toString().substring(0, 8)}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val urlString = if (serviceType == ServiceType.CATBOX) {
            "https://catbox.moe/user/api.php"
        } else {
            "https://litterbox.catbox.moe/resources/internals/api.php"
        }

        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            useCaches = false
            setChunkedStreamingMode(16384)
            setRequestProperty(
                "User-Agent",
                "Dalvik/2.1.0 (Linux; U; Android 10; Android Phone Build/QP1A.190711.020)"
            )
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            var totalBytesWritten = 0L
            var lastUpdate = 0L

            DataOutputStream(connection.outputStream).use { output ->

                fun writeFormField(name: String, value: String) {
                    output.writeBytes(twoHyphens + boundary + lineEnd)
                    output.writeBytes("Content-Disposition: form-data; name=\"$name\"$lineEnd")
                    output.writeBytes(lineEnd)
                    output.writeBytes(value + lineEnd)
                }

                writeFormField("reqtype", "fileupload")
                if (serviceType == ServiceType.CATBOX && !userHash.isNullOrEmpty()) {
                    writeFormField("userhash", userHash)
                }
                if (serviceType == ServiceType.LITTERBOX) {
                    writeFormField("time", time)
                }

                output.writeBytes(twoHyphens + boundary + lineEnd)
                output.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"$safeFileName\"$lineEnd")
                output.writeBytes("Content-Type: $mimeType$lineEnd")
                output.writeBytes(lineEnd)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(16384) // 16KB Buffer
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesWritten += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdate > 100) {
                            if (totalFileSize > 0) {
                                onProgress(totalBytesWritten.toFloat() / totalFileSize.toFloat())
                            }
                            lastUpdate = currentTime
                        }
                    }
                }
                output.writeBytes(lineEnd)
                output.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                output.flush()
            }

            onProgress(1f)

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else if (responseCode == 504 || responseCode == 502) {
                throw Exception("Server is overloaded. Please try uploading again.")
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (errorMsg.trim().startsWith("<")) {
                    throw Exception("Upload failed: HTTP $responseCode")
                } else {
                    throw Exception("Upload failed: HTTP $responseCode $errorMsg")
                }
            }

        } finally {
            connection.disconnect()
        }
    }
}