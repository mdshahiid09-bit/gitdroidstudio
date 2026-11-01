package com.gitdroidstudio.shahid.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ApkInstaller {

    /**
     * Downloads an APK from the specified direct URL and launches the package installer.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        authToken: String? = null,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val requestBuilder = Request.Builder().url(apkUrl)
            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "token $authToken")
                requestBuilder.header("Accept", "application/octet-stream")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download APK: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val contentLength = body.contentLength()

            val apkDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads")
            if (!apkDir.exists()) apkDir.mkdirs()

            val apkFile = File(apkDir, "gitdroid_latest.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                installApkFile(context, apkFile)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApkFile(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+ (Oreo), check if app has permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}
