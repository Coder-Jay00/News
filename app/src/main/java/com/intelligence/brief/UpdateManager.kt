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
data class VersionInfo(
    val version: String,
    val url: String
)

// Keep for backward compatibility (unused)
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
    private val currentVersion = "v" + BuildConfig.VERSION_NAME 
    
    // NEW: Use Vercel-hosted JSON instead of GitHub Releases API
    private val versionUrl = "https://brief-iota.vercel.app/version.json"

    suspend fun checkForUpdate(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch version.json from Vercel
                val response = client.get(versionUrl).bodyAsText()
                val versionInfo = json.decodeFromString<VersionInfo>(response)
                
                val cloudVersion = "v" + versionInfo.version.removePrefix("v") // Normalize
                
                // 2. Compare Versions
                android.util.Log.d("UpdateManager", "Current: $currentVersion, Cloud: $cloudVersion")
                
                if (isNewerVersion(cloudVersion, currentVersion)) {
                    // Return (Version, URL)
                    return@withContext Pair(cloudVersion, versionInfo.url)
                } 
                
                android.util.Log.d("UpdateManager", "App is up to date (or newer than cloud).")
                return@withContext null
            } catch (e: Exception) {
                android.util.Log.e("UpdateManager", "Update check failed: ${e.message}")
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    private fun isNewerVersion(cloud: String, local: String): Boolean {
        try {
            val v1 = cloud.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val v2 = local.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            
            val limit = maxOf(v1.size, v2.size)
            for (i in 0 until limit) {
                val num1 = v1.getOrElse(i) { 0 }
                val num2 = v2.getOrElse(i) { 0 }
                if (num1 > num2) return true
                if (num1 < num2) return false
            }
            return false // Equal
        } catch (e: Exception) {
            return cloud != local // Fallback
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
