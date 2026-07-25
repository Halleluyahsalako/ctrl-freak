package com.hal.ctrlfreak.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

// Mirrors extension/src/hal-drive.ts — same two REST calls, same
// drive.file scope (see HalAuth.kt), same multipart upload shape.

private val halHttpClient = OkHttpClient()

suspend fun halUploadFileToDrive(
    accessToken: String,
    bytes: ByteArray,
    mimeType: String,
    name: String,
): String = withContext(Dispatchers.IO) {
    val metadata = JSONObject().put("name", name).toString()
        .toRequestBody("application/json; charset=UTF-8".toMediaType())
    val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addPart(metadata)
        .addPart(bytes.toRequestBody(mimeType.toMediaType()))
        .build()

    val request = Request.Builder()
        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        .header("Authorization", "Bearer $accessToken")
        .post(body)
        .build()

    halHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Drive upload failed: ${response.code} ${response.body?.string()}")
        }
        JSONObject(response.body!!.string()).getString("id")
    }
}

suspend fun halFetchDriveFileBytes(accessToken: String, driveFileId: String): ByteArray =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .build()

        halHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Drive fetch failed: ${response.code} ${response.body?.string()}")
            }
            response.body!!.bytes()
        }
    }
