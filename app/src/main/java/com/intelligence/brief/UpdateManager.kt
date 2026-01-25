package com.intelligence.brief

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val browser_download_url: String,
    val name: String
)

class UpdateManager(private val context: Context) {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val currentVersion = "v1.2.8" 
    private val repoUrl = "https://api.github.com/repos/Coder-Jay00/News/releases/latest"

    suspend fun checkForUpdate(): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch Latest Release
                val response = client.get(repoUrl).bodyAsText()
                val release = json.decodeFromString<GitHubRelease>(response)
                
                // 2. Compare Versions (Simple String Comparison for now)
                // In production, use a proper SemVer parser
                if (release.tag_name != currentVersion) {
                    // Return the download URL (check for APK, then ZIP, then point to Website)
                    val asset = release.assets.find { it.name.endsWith(".apk") } 
                                ?: release.assets.find { it.name.endsWith(".zip") }
                    
                    return@withContext asset?.browser_download_url ?: "https://brief-iota.vercel.app/"
                }
                return@withContext null
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    fun triggerUpdate(url: String, version: String = "latest"): Long {
        // Clean up any old update files first to prevent confusion
        deleteOldUpdates()
        
        val fileName = "brief_update_${version.replace(".", "_")}.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Brief. Update ($version)")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    fun getDownloadedFileUri(version: String = "latest"): Uri? {
        val fileName = "brief_update_${version.replace(".", "_")}.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun isUpdateDownloaded(version: String = "latest"): Boolean {
        val fileName = "brief_update_${version.replace(".", "_")}.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        return file.exists() && file.length() > 1024 * 1024
    }

    fun deleteUpdateFile(version: String = "latest") {
        val fileName = "brief_update_${version.replace(".", "_")}.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    fun deleteOldUpdates() {
        try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            dir?.listFiles()?.forEach { file ->
                if (file.name.startsWith("brief_update") && file.name.endsWith(".apk")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
