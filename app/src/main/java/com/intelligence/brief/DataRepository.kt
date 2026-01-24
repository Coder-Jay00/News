package com.intelligence.brief

import android.content.Context
import android.content.SharedPreferences
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

// Data Models
@Serializable
data class Article(
    val id: String = "",
    val title: String,
    val summary: String,
    val source: String,
    val published: String,
    val category: String,
    val trust_badge: String,
    val icon: String,
    val link: String,
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
        if (interests.isEmpty()) return emptyList()

        try {
            // Calculate range for pagination
            val fromIndex = page * PAGE_SIZE
            val toIndex = fromIndex + PAGE_SIZE - 1

            return supabase.from("articles")
                .select() {
                    filter {
                        // Efficient Postgrest filtering: 'category' MUST be in the 'interests' list
                        isIn("category", interests.toList())
                    }
                    // Sort by publication date DESC (Newest First)
                    order("published", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    // Pagination on the server
                    range(fromIndex.toLong(), toIndex.toLong())
                }
                .decodeList<Article>()
        } catch (e: Exception) {
            android.util.Log.e("DataRepo", "Error fetching data from Supabase", e)
            return emptyList()
        }
    }

    // Legacy method for compatibility
    suspend fun fetchDailyBrief(): List<Article> = fetchArticles(0)
}
