package com.intelligence.brief

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val currentVersion = "v1.0.0" 
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
                    // Return the download URL (check for .apk first, then .zip)
                    val asset = release.assets.find { it.name.endsWith(".apk") } 
                                ?: release.assets.find { it.name.endsWith(".zip") }
                    return@withContext asset?.browser_download_url ?: release.html_url
                }
                return@withContext null
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    fun triggerUpdate(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
