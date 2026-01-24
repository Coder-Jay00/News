package com.intelligence.brief

import android.content.Context
import android.util.Log
import androidx.work.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker that periodically checks for new articles and shows notifications
 */
class NewsSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "news_sync_worker"
        private const val TAG = "NewsSyncWorker"
        private const val PREF_LAST_ARTICLE_COUNT = "last_article_count"

        /**
         * Schedule periodic background sync (every 1 hour, minimum 15 min for Android)
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<NewsSyncWorker>(
                1, TimeUnit.HOURS  // Check every hour
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            
            Log.d(TAG, "Background news sync scheduled")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting background news sync...")
            
            // Initialize Supabase client
            val supabase = createSupabaseClient(
                supabaseUrl = "https://gbcdlnfhazmyndglqcek.supabase.co",
                supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdiY2RsbmZoYXpteW5kZ2xxY2VrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkyMjU4MDUsImV4cCI6MjA4NDgwMTgwNX0.I0GHeAkSmccNMFsaqVX7dzxzRUuysmrIfO0WZdYh8fg"
            ) {
                install(Postgrest)
            }

            // Fetch current article count
            val articles = supabase.from("articles")
                .select()
                .decodeList<Article>()

            val currentCount = articles.size
            val prefs = context.getSharedPreferences("news_sync", Context.MODE_PRIVATE)
            val lastCount = prefs.getInt(PREF_LAST_ARTICLE_COUNT, 0)

            Log.d(TAG, "Articles: current=$currentCount, last=$lastCount")

            // If there are new articles, show notification
            if (currentCount > lastCount && lastCount > 0) {
                val newCount = currentCount - lastCount
                val topHeadline = articles.firstOrNull()?.title ?: "New stories available"
                
                NotificationHelper.showNewArticlesNotification(
                    context,
                    newCount,
                    topHeadline
                )
                Log.d(TAG, "Notification shown for $newCount new articles")
            }

            // Save current count
            prefs.edit().putInt(PREF_LAST_ARTICLE_COUNT, currentCount).apply()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }
}
