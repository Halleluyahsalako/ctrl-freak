package com.hal.ctrlfreak.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

// Mirrors extension/src/hal-drive.ts — same two REST calls, same
// drive.file scope (see HalAuth.kt), same multipart upload shape.

private val halHttpClient = OkHttpClient()

private const val HAL_DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val HAL_DRIVE_FOLDER_NAME = "Ctrl+Freak"
private const val HAL_DRIVE_FOLDER_MIME = "application/vnd.google-apps.folder"

// Cached for the process lifetime — cheap enough to refetch on next launch.
private var halFolderIdCache: String? = null

// Everything used to land loose in the root of My Drive with no way to find
// it again. Every upload now goes into one "Ctrl+Freak" folder instead.
private suspend fun halGetOrCreateAppFolder(accessToken: String): String = withContext(Dispatchers.IO) {
    halFolderIdCache?.let { return@withContext it }

    val query = "name = '$HAL_DRIVE_FOLDER_NAME' and mimeType = '$HAL_DRIVE_FOLDER_MIME' and trashed = false"
    val searchUrl = HAL_DRIVE_FILES_URL.toHttpUrl().newBuilder()
        .addQueryParameter("q", query)
        .addQueryParameter("spaces", "drive")
        .addQueryParameter("fields", "files(id)")
        .build()
    val searchRequest = Request.Builder().url(searchUrl).header("Authorization", "Bearer $accessToken").build()

    halHttpClient.newCall(searchRequest).execute().use { response ->
        if (response.isSuccessful) {
            val files = JSONObject(response.body!!.string()).getJSONArray("files")
            if (files.length() > 0) {
                val id = files.getJSONObject(0).getString("id")
                halFolderIdCache = id
                return@withContext id
            }
        }
    }

    val createBody = JSONObject().put("name", HAL_DRIVE_FOLDER_NAME).put("mimeType", HAL_DRIVE_FOLDER_MIME).toString()
        .toRequestBody("application/json; charset=UTF-8".toMediaType())
    val createRequest = Request.Builder()
        .url(HAL_DRIVE_FILES_URL)
        .header("Authorization", "Bearer $accessToken")
        .post(createBody)
        .build()

    halHttpClient.newCall(createRequest).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Drive folder create failed: ${response.code} ${response.body?.string()}")
        }
        val id = JSONObject(response.body!!.string()).getString("id")
        halFolderIdCache = id
        id
    }
}

suspend fun halUploadFileToDrive(
    accessToken: String,
    bytes: ByteArray,
    mimeType: String,
    name: String,
): String = withContext(Dispatchers.IO) {
    val folderId = halGetOrCreateAppFolder(accessToken)
    val metadata = JSONObject().put("name", name).put("parents", JSONArray().put(folderId)).toString()
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
