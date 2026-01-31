package com.intelligence.brief

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Data Models
@Serializable
data class Article(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    @SerialName("ai_summary")
    val aiSummary: String? = null,
    val source: String = "",
    val published: String = "",
    val category: String = "",
    @SerialName("trust_badge")
    val trustBadge: String = "",
    val icon: String = "",
    val link: String = "",
    val tier: Int? = null,
    // Feature 5: Trust Score
    @SerialName("trust_score")
    val trustScore: Int? = null,
    @SerialName("trust_reason")
    val trustReason: String? = null,
    @SerialName("related_links")
    val relatedLinks: List<String>? = null
)

// Feature 9: Morning Reel Model
@Serializable
data class DailyReel(
    val title: String = "",
    val summary: String = "",
    val stories: List<Article> = emptyList()
)

@Serializable
data class ReelWrapper(
    val content: DailyReel
)

@Serializable
data class WatchlistEntry(
    @SerialName("user_fcm_token")
    val token: String,
    val keyword: String
)

class DataRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    
    // Initialize Supabase (Anon Key is safe for public read-only)
    private val supabase = createSupabaseClient(
        supabaseUrl = "https://gbcdlnfhazmyndglqcek.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdiY2RsbmZoYXpteW5kZ2xxY2VrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkyMjU4MDUsImV4cCI6MjA4NDgwMTgwNX0.I0GHeAkSmccNMFsaqVX7dzxzRUuysmrIfO0WZdYh8fg" 
    ) {
        install(Postgrest)
    }

    companion object {
        const val PAGE_SIZE = 20
    }

    // Onboarding Logic
    fun isOnboarded(): Boolean = prefs.getBoolean("onboarded", false)

    fun saveInterests(interests: Set<String>) {
        prefs.edit()
            .putStringSet("interests", interests)
            .putBoolean("onboarded", true)
            .apply()
    }

    fun getInterests(): Set<String> {
        return prefs.getStringSet("interests", setOf("AI & Frontiers", "Cybersecurity")) ?: emptySet()
    }
    
    fun getFcmToken(): String? {
       return prefs.getString("fcm_token", null) 
    }
    
    fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }

    // Data Fetching with Pagination - Server Side Filtering & Sorting
    suspend fun fetchArticles(page: Int = 0, category: String? = null): List<Article> {
        val interests = getInterests()
        val region = getRegion()
        android.util.Log.d("DataRepo", "Fetching articles for page $page with interests: $interests, filter: $category, region: $region")
        
        if (interests.isEmpty()) {
            android.util.Log.w("DataRepo", "No interests selected, returning empty list")
            return emptyList()
        }

        try {
            val fromIndex = page * PAGE_SIZE
            val toIndex = fromIndex + PAGE_SIZE - 1

            val response = supabase.from("articles")
                .select() {
                    filter {
                        // Region Filter
                        if (region != "Global") {
                            eq("region", region)
                        }
                        
                        // Category Filter
                        if (!category.isNullOrEmpty() && category != "All") {
                            eq("category", category)
                        } else {
                            isIn("category", interests.toList())
                        }
                    }
                    order("published", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    range(fromIndex.toLong(), toIndex.toLong())
                }
            
            val list = response.decodeList<Article>()
            if (page == 0 && list.isNotEmpty()) {
                saveCachedFeed(list) // Cache the first page
            }
            return list
            
        } catch (e: Exception) {
            android.util.Log.e("DataRepo", "Error fetching data: ${e.message}", e)
            return emptyList()
        }
    }
    
    // Feature 9: Fetch Morning Reel
    suspend fun fetchMorningReel(): DailyReel? {
        try {
            // Get today's reel
            val response = supabase.from("daily_briefings")
                .select() {
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(1)
                }
            val wrappers = response.decodeList<ReelWrapper>()
            return wrappers.firstOrNull()?.content
        } catch (e: Exception) {
            android.util.Log.e("DataRepo", "Error fetching reel: ${e.message}")
            return null
        }
    }
    
    // Feature 10: Watchlist Management
    suspend fun addWatchlistKeyword(keyword: String) {
        val token = getFcmToken()
        if (token.isNullOrEmpty()) {
             android.util.Log.e("DataRepo", "Cannot add watchlist: No FCM Token found")
             return
        }
        
        try {
            val entry = WatchlistEntry(token = token, keyword = keyword)
            supabase.from("user_watchlists").insert(entry)
            android.util.Log.d("DataRepo", "Watchlist keyword added: $keyword")
        } catch (e: Exception) {
            android.util.Log.e("DataRepo", "Error adding watchlist: ${e.message}")
        }
    }

    // Legacy method for compatibility
    suspend fun fetchDailyBrief(): List<Article> = fetchArticles(0)

    // Caching Logic (File-based JSON)
    fun getCachedFeed(): List<Article> {
        return try {
            val file = java.io.File(context.cacheDir, "feed_cache.json")
            if (file.exists()) {
                val jsonString = file.readText()
                val list = kotlinx.serialization.json.Json.decodeFromString<List<Article>>(jsonString)
                android.util.Log.d("DataRepo", "Loaded ${list.size} articles from cache")
                list
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("DataRepo", "Cache load failed: ${e.message}")
            emptyList()
        }
    }

    private fun saveCachedFeed(list: List<Article>) {
        try {
            val jsonString = kotlinx.serialization.json.Json.encodeToString(list)
            val file = java.io.File(context.cacheDir, "feed_cache.json")
            file.writeText(jsonString)
            android.util.Log.d("DataRepo", "Saved ${list.size} articles to cache")
        } catch (e: Exception) {
            android.util.Log.e("DataRepo", "Cache save failed: ${e.message}")
        }
    }

    // Feature 6 (Retention): Region Preference
    fun saveRegion(region: String) {
        prefs.edit().putString("news_region", region).apply()
    }

    fun getRegion(): String {
        return prefs.getString("news_region", "Global") ?: "Global"
    }

    // Feature 6 (Retention): Bookmarks System
    fun getBookmarks(): List<Article> {
        val json = prefs.getString("bookmarks", "[]") ?: "[]"
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isBookmarked(articleId: String): Boolean {
        return getBookmarks().any { it.id == articleId }
    }

    fun toggleBookmark(article: Article) {
        val current = getBookmarks().toMutableList()
        val index = current.indexOfFirst { it.id == article.id }
        if (index != -1) {
            current.removeAt(index) // Remove if exists
        } else {
            current.add(0, article) // Add to top if new
        }
        prefs.edit().putString("bookmarks", Json.encodeToString(current)).apply()
    }

    fun removeBookmark(articleId: String) {
        val current = getBookmarks().toMutableList()
        val index = current.indexOfFirst { it.id == articleId }
        if (index != -1) {
            current.removeAt(index)
            prefs.edit().putString("bookmarks", Json.encodeToString(current)).apply()
        }
    }

    // Feature 6 (Retention): History System (Max 20)
    fun getHistory(): List<Article> {
        val json = prefs.getString("reading_history", "[]") ?: "[]"
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToHistory(article: Article) {
        val current = getHistory().toMutableList()
        // Remove duplicate if exists to move it to top
        current.removeAll { it.id == article.id }
        current.add(0, article)
        
        // Trim to max 20
        if (current.size > 20) {
            current.removeAt(current.lastIndex)
        }
        
        prefs.edit().putString("reading_history", Json.encodeToString(current)).apply()
    }

    // Trigger Cloud Sync (GitHub Action via Vercel Relay)
    suspend fun triggerSync() {
        withContext(Dispatchers.IO) {
            try {
                val client = HttpClient()
                client.post("https://brief-iota.vercel.app/api/trigger-sync")
                android.util.Log.d("DataRepo", "Cloud Sync Triggered Successfully")
            } catch (e: Exception) {
                android.util.Log.e("DataRepo", "Failed to trigger Cloud Sync: ${e.message}")
            }
        }
    }
}
