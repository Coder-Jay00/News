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

    // Data Fetching with Pagination
    suspend fun fetchArticles(page: Int = 0): List<Article> {
        val interests = getInterests()
        try {
            val allArticles = supabase.from("articles")
                .select()
                .decodeList<Article>()
                .filter { it.category in interests }
                .sortedByDescending { it.published }
            
            // Pagination: skip previous pages, take PAGE_SIZE
            val startIndex = page * PAGE_SIZE
            if (startIndex >= allArticles.size) return emptyList()
            
            return allArticles.drop(startIndex).take(PAGE_SIZE)
        } catch (e: Exception) {
            android.util.Log.e("DataRepo", "Error fetching data", e)
            e.printStackTrace()
            return emptyList()
        }
    }

    // Legacy method for compatibility
    suspend fun fetchDailyBrief(): List<Article> = fetchArticles(0)
}
