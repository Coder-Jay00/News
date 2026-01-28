package com.intelligence.brief

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
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

class DataRepository(context: Context) {
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
    suspend fun fetchArticles(page: Int = 0): List<Article> {
        val interests = getInterests()
        android.util.Log.d("DataRepo", "Fetching articles for page $page with interests: $interests")
        
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
                        isIn("category", interests.toList())
                    }
                    order("published", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    range(fromIndex.toLong(), toIndex.toLong())
                }
            
            val list = response.decodeList<Article>()
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
