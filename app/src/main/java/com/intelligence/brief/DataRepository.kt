package com.intelligence.brief

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

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
    val tier: Int? = null
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
        const val PAGE_SIZE = 15
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
            android.util.Log.d("DataRepo", "Fetched ${list.size} articles for categories: $interests")
            
            // FALLBACK: If list is empty, try fetching ANY articles to see if DB is even working
            if (list.isEmpty() && page == 0) {
                android.util.Log.d("DataRepo", "Filtered list empty. Trying fallback fetch for ANY articles...")
                val fallbackResponse = supabase.from("articles")
                    .select() {
                        order("published", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(5)
                    }
                val fallbackList = fallbackResponse.decodeList<Article>()
                android.util.Log.d("DataRepo", "Fallback fetch returned ${fallbackList.size} items.")
                return fallbackList
            }

            return list
            
        } catch (e: Exception) {
            android.util.Log.e("DataRepo", "FATAL ERROR fetching data: ${e.message}", e)
            // Print stack trace to see exactly where it fails (likely JSON decoding)
            e.printStackTrace()
            return emptyList()
        }
    }

    // Legacy method for compatibility
    suspend fun fetchDailyBrief(): List<Article> = fetchArticles(0)
}
