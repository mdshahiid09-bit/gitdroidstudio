package com.gitdroidstudio.shahid.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ApkInstaller {

    /**
     * Downloads an artifact ZIP from GitHub Actions, extracts the APK,
     * verifies its validity (checks AndroidManifest.xml), and triggers installation.
     */
    suspend fun downloadArtifactAndInstall(
        context: Context,
        archiveUrl: String,
        authToken: String?,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val requestBuilder = Request.Builder().url(archiveUrl)
            if (!authToken.isNullOrBlank()) {
                val headerToken = if (authToken.startsWith("token ") || authToken.startsWith("Bearer ")) authToken else "token $authToken"
                requestBuilder.header("Authorization", headerToken)
                requestBuilder.header("Accept", "application/vnd.github+json")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to download artifact: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty artifact body"))
            val cacheDir = File(context.cacheDir, "artifacts").apply { if (!exists()) mkdirs() }
            val zipFile = File(cacheDir, "app-debug.zip")

            body.byteStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            // Extract the APK from the downloaded ZIP
            var extractedApk: File? = null
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".apk", ignoreCase = true)) {
                        val apkDest = File(cacheDir, "extracted-debug.apk")
                        FileOutputStream(apkDest).use { fos ->
                            val buf = ByteArray(8 * 1024)
                            var len: Int
                            while (zis.read(buf).also { len = it } != -1) {
                                fos.write(buf, 0, len)
                            }
                            fos.flush()
                        }
                        extractedApk = apkDest
                        break
                    }
                    entry = zis.nextEntry
                }
            }

            val apk = extractedApk ?: return@withContext Result.failure(Exception("No APK file found inside artifact ZIP"))

            // Verify valid APK by checking for AndroidManifest.xml inside
            val isValidApk = try {
                ZipFile(apk).use { apkZip ->
                    apkZip.getEntry("AndroidManifest.xml") != null
                }
            } catch (_: Exception) {
                false
            }

            if (!isValidApk) {
                return@withContext Result.failure(Exception("Corrupted APK: AndroidManifest.xml not found"))
            }

            withContext(Dispatchers.Main) {
                installApkFile(context, apk)
            }

            Result.success(apk)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApkFile(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

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

    /**
     * Copies the APK file to the device's public Downloads directory.
     */
    suspend fun saveApkToDownloads(context: Context, apkFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "GitDroidStudio-${System.currentTimeMillis()}.apk"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Failed to create download entry"))

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(apkFile).use { input ->
                        input.copyTo(out)
                    }
                }
                Result.success("Saved to Downloads: $fileName")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, fileName)
                FileInputStream(apkFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success("Saved to Downloads: ${destFile.absolutePath}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads any generic artifact zip (e.g. for non-Android projects) directly to Downloads directory.
     */
    suspend fun downloadGenericArtifactZipToDownloads(
        context: Context,
        archiveUrl: String,
        artifactName: String,
        authToken: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val requestBuilder = Request.Builder().url(archiveUrl)
            if (!authToken.isNullOrBlank()) {
                val headerToken = if (authToken.startsWith("token ") || authToken.startsWith("Bearer ")) authToken else "token $authToken"
                requestBuilder.header("Authorization", headerToken)
                requestBuilder.header("Accept", "application/vnd.github+json")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty artifact body"))
            val fileName = "$artifactName-${System.currentTimeMillis()}.zip"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Failed to create download entry"))

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }
                Result.success("Saved to Downloads: $fileName")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, fileName)
                FileOutputStream(destFile).use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out)
                    }
                }
                Result.success("Saved to Downloads: ${destFile.absolutePath}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
