package com.android.catboxclient

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
        val safeFileName = getSafeFileName(context, uri)
        val file = uriToFile(context, uri, safeFileName) ?: throw Exception("Cannot read file")

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
            setChunkedStreamingMode(8192)
            setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 10; Android Phone Build/QP1A.190711.020)")
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            val totalFileSize = file.length()
            var totalBytesWritten = 0L

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
                output.writeBytes("Content-Type: ${getMimeType(file)}$lineEnd")
                output.writeBytes(lineEnd)

                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        output.flush()

                        totalBytesWritten += bytesRead
                        val progress = if (totalFileSize > 0) totalBytesWritten.toFloat() / totalFileSize.toFloat() else 0f
                        onProgress(progress)
                    }
                }
                output.writeBytes(lineEnd)

                output.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                output.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            } else if (responseCode == 504 || responseCode == 502) {
                throw Exception("Server is overloaded. Please try uploading again.")
            } else {
                val errorStream = connection.errorStream
                val errorMsg = errorStream?.bufferedReader()?.use { it.readText() } ?: ""

                if (errorMsg.trim().startsWith("<")) {
                    throw Exception("Upload failed: HTTP $responseCode")
                } else {
                    throw Exception("Upload failed: HTTP $responseCode $errorMsg")
                }
            }

        } finally {
            connection.disconnect()
            try { file.delete() } catch (e: Exception) {}
        }
    }

    private fun getSafeFileName(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        val extension = if (mimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "tmp"
        } else {
            uri.path?.substringAfterLast(".") ?: "tmp"
        }
        return "upload_${System.currentTimeMillis()}.$extension"
    }

    private fun uriToFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, fileName)
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMimeType(file: File): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
    }
}