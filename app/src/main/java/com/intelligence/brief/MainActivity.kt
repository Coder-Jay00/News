package com.intelligence.brief

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import com.intelligence.brief.ui.theme.BriefTheme
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import java.text.SimpleDateFormat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.toSize
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.onGloballyPositioned
import java.util.TimeZone
import java.util.Locale
import java.util.* 
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.io.File

fun getRelativeTime(publishedAt: String?): String {
    if (publishedAt.isNullOrEmpty()) return ""
    
    // Comprehensive list of formats to handle RSS, SQL, and ISO strings
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "EEE, dd MMM yyyy HH:mm:ss Z" // Standard RSS format fallback
    )
    
    return try {
        val input = publishedAt.trim()
        
        var date: Date? = null
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                if (format.contains("'Z'") || format.contains("Z")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                } else if (format.contains("'T'")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                date = sdf.parse(input)
                if (date != null) break
            } catch (e: Exception) { continue }
        }

        if (date == null) {
            try {
                val clean = input.replace("Z", "").split("+")[0].split(" ").joinToString("T")
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                date = sdf.parse(clean)
            } catch (e: Exception) { null }
        }

        if (date == null) return ""
        
        val now = System.currentTimeMillis()
        val diff = now - date.time
        
        if (diff < 0) return "Just now"

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var repository: DataRepository
    
    // Permission request launcher for Android 13+ notifications
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result - notifications will work if granted
    }

    private var downloadId: Long = -1
    private lateinit var updateManager: UpdateManager
    
    // Update UI State
    // State removed per user request

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = DataRepository(this)
        
        // Setup notifications
        NotificationHelper.createNotificationChannel(this)
        requestNotificationPermission()
        
        // Subscribe to Firebase 'news' topic for push notifications
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic("news")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.d("FCM", "Subscribed to news topic")
                }
            }
        
        // Save FCM Token for Watchlist (Feature 10)
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                repository.saveFcmToken(token)
                android.util.Log.d("FCM", "Token saved: ${token.take(10)}...")
            }
        }
        
        android.util.Log.d("MainActivity", "onCreate Intent: ${intent?.extras?.keySet()?.joinToString()}")
        
        // Background sync (as fallback)
        NewsSyncWorker.schedule(this)

        // Setup UpdateManager
        // Update functionality removed

        // REMOVED explicit checkForUpdates() here because onResume (below) handles it.
        // checkForUpdates()


        setContent {
            // Theme state
            var isDarkMode by remember { mutableStateOf<Boolean?>(null) }
            // Region state (Hoisted for App-wide refresh)
            val currentRegionState = remember { mutableStateOf(repository.getRegion()) }
            
            // Lifecycle monitoring removed per user request
            
            // Compute effective theme (null = follow system automatically)
            val useDarkTheme = isDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
            
            BriefTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box {
                        AppNavigator(
                            repository = repository,
                            isDarkMode = isDarkMode,
                            currentRegion = currentRegionState.value,
                            onToggleTheme = { isDarkMode = if (isDarkMode == true) false else true },
                            onRegionChange = { currentRegionState.value = it }
                        )
                        
                        // Update Dialog removed per user request
                    }
                }
            }
        }
        
        // Handle incoming intent
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        // 1. Prioritize Extras (from NotificationHelper)
        var url = intent.getStringExtra("url")
        
        // 2. Fallback to Data URI (from system browser/other apps)
        if (url == null) {
            url = intent.dataString
        }

        if (url.isNullOrEmpty()) return

        // CLEAR the intent state to prevent "sticky" behavior on rotation or resume
        intent.removeExtra("url")
        try { intent.data = null } catch (e: Exception) {}
        // DO NOT call setIntent(Intent()) here as it can disrupt some activity lifecycle states

        android.util.Log.d("MainActivity", "Processing URL: $url")
        
        val cleanUrl = url.trim().lowercase()
        val isUpdateUrl = cleanUrl.endsWith(".apk") || 
                         cleanUrl.contains("github.com") || 
                         cleanUrl.contains("vercel.app") || 
                         cleanUrl.contains("brief")
                         
        // Update logic removed. All URLs open in browser.
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(browserIntent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Browser redirect failed", e)
        }
    }

    // checkForUpdates removed per user request

    // installApk removed per user request

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationHelper.hasNotificationPermission(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Receiver unregister removed
    }
}

// ... (Existing imports)

// Feature 9: Morning Reel Screen
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MorningReelScreen(
    repository: DataRepository,
    onBack: () -> Unit
) {
    var reel by remember { mutableStateOf<DailyReel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val pagerState = rememberPagerState { reel?.stories?.size ?: 0 }

    LaunchedEffect(true) {
        try {
            kotlinx.coroutines.withTimeout(5000L) {
                reel = repository.fetchMorningReel()
            }
        } catch (e: Exception) {
            android.util.Log.e("Reel", "Timeout or Error: $e")
            reel = null
        }
        isLoading = false
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            if (!isLoading && reel != null) {
                // Progress Indicators at Top
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(pagerState.pageCount) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.White else Color.Gray.copy(alpha = 0.5f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                    }
                }
                
                // Close Button Layout
                Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, end = 16.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd)) {
                        Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            } else if (reel == null || reel?.stories.isNullOrEmpty()) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No Daily Pulse Available", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onBack) { Text("Go Back", color = Color.LightGray) }
                }
            } else {
                val stories = reel!!.stories
                
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val story = stories[page]
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Category Badge
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(bottom = 24.dp)
                            ) {
                                Text(
                                    story.category,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Title
                            Text(
                                text = story.title,
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(bottom = 32.dp)
                            )
                            
                            Divider(color = Color.Gray.copy(alpha=0.3f), thickness = 1.dp, modifier = Modifier.width(100.dp))
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            // Summary
                            Text(
                                text = HtmlTextMapper.fromHtml(story.aiSummary ?: story.summary),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.LightGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 28.sp
                            )
                            
                            Spacer(modifier = Modifier.height(48.dp))
                            
                            // "Read More" Button
                            Button(
                                onClick = { uriHandler.openUri(story.link) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                            ) {
                                Text("Read Full Story", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Source: ${story.source}", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                        
                        // Swipe Hint (Only on first page)
                        if (page == 0) {
                            Column(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Swipe Left for Next Story", color = Color.Gray.copy(alpha=0.7f), style = MaterialTheme.typography.labelSmall)
                                Text("→", color = Color.Gray.copy(alpha=0.7f), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ... Header of FeedScreen (add Button to open Reel) ...
// (Note: Since I'm replacing chunks, I'll modify AppNavigator and FeedScreen below)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigator(
    repository: DataRepository,
    isDarkMode: Boolean?,
    currentRegion: String,
    onToggleTheme: () -> Unit,
    onRegionChange: (String) -> Unit
) {
    var isOnboarded by remember { mutableStateOf(repository.isOnboarded()) }
    var currentScreen by remember { mutableStateOf("feed") } // simple router

    when (currentScreen) {
        "settings" -> {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            SettingsScreen(
                repository = repository,
                onBack = { currentScreen = "feed" },
                onOpenUrl = { url -> uriHandler.openUri(url) },
                currentRegionState = currentRegion,
                onRegionChange = onRegionChange
            )
        }
        "reel" -> {
            MorningReelScreen(
                repository = repository,
                onBack = { currentScreen = "feed" }
            )
        }
        "feed" -> {
            if (isOnboarded) {
                FeedScreen(
                    repository = repository,
                    isDarkMode = isDarkMode,
                    currentRegion = currentRegion,
                    onToggleTheme = onToggleTheme,
                    onOpenSettings = { currentScreen = "settings" },
                    onOpenReel = { currentScreen = "reel" }
                )
            } else {
                OnboardingScreen { interests ->
                    repository.saveInterests(interests)
                    isOnboarded = true
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    repository: DataRepository,
    isDarkMode: Boolean?,
    currentRegion: String,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReel: () -> Unit
) {
    // ... (Existing state items) ...
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "India News", "World News", "Business", "Technology", "Science", "Health", "Politics", "Entertainment", "Sports", "AI & Frontiers", "Cybersecurity")

    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Refresh function - reloads from scratch
    // Refresh function - reloads from scratch
    suspend fun refresh() {
        withContext(Dispatchers.Main) {
            isRefreshing = true
        }
        withContext(Dispatchers.IO) {
            repository.triggerSync()
            delay(2000) // Visual Feedback Delay
            val newArticles = repository.fetchArticles(0, selectedCategory)
            withContext(Dispatchers.Main) {
                currentPage = 0
                if (newArticles.isNotEmpty()) {
                    articles = newArticles
                    hasMore = newArticles.size >= DataRepository.PAGE_SIZE
                }
                isRefreshing = false
            }
        }
    }

    // Initial load
    LaunchedEffect(selectedCategory, currentRegion) {
        isLoading = true
        currentPage = 0
        hasMore = true
        articles = emptyList() // Clear current list when switching category
        
        try {
            // Load cache only if "All" is selected (since cache is mixed)
            if (selectedCategory == "All") {
                 val cached = withContext(Dispatchers.IO) { repository.getCachedFeed() }
                 if (cached.isNotEmpty()) articles = cached
            }

            val fresh = repository.fetchArticles(0, selectedCategory)
            if (fresh.isNotEmpty()) {
                articles = fresh
                hasMore = fresh.size >= DataRepository.PAGE_SIZE
            }
        } catch (e: Exception) {
            android.util.Log.e("Feed", "Network Error: ${e.message}")
        } finally {
            isLoading = false
        }
    }
    
    // ... (Scroll Listener) ...
    LaunchedEffect(listState) {
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 5 
        }.collect { nearBottom ->
            if (nearBottom && hasMore && !isLoading && !isRefreshing && articles.isNotEmpty()) {
                isLoading = true
                currentPage++
                val moreArticles = repository.fetchArticles(currentPage, selectedCategory)
                if (moreArticles.isEmpty()) { hasMore = false } 
                else { 
                    // Deduplicate to prevent repeated entries
                    articles = (articles + moreArticles).distinctBy { it.link }
                    hasMore = moreArticles.size >= DataRepository.PAGE_SIZE
                }
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Brief.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("v${BuildConfig.VERSION_NAME} Intelligence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) { Text("⚙️", style = MaterialTheme.typography.titleLarge) }
                },
                actions = {
                    // Feature 9: Morning Reel Entry Point
                    IconButton(onClick = onOpenReel) {
                        Text("🎬", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onToggleTheme) {
                        Text(if (isDarkMode == true) "☀️" else "🌙", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
         // Feature: YouTube-style Category Filter Chips
         Column(modifier = Modifier.padding(padding)) {
             LazyRow(
                 modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                 contentPadding = PaddingValues(horizontal = 16.dp),
                 horizontalArrangement = Arrangement.spacedBy(8.dp)
             ) {
                 items(categories) { category ->
                     val isSelected = selectedCategory == category
                     FilterChip(
                         selected = isSelected,
                         onClick = { selectedCategory = category },
                         label = { Text(category) },
                         colors = FilterChipDefaults.filterChipColors(
                             selectedContainerColor = MaterialTheme.colorScheme.primary,
                             selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                         )
                     )
                 }
             }

             val pagerState = rememberPagerState(pageCount = { categories.size })
             val scope = rememberCoroutineScope()
             var selectedArticle by remember { mutableStateOf<Article?>(null) }
             val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
             
             // Sync: Chip Click -> Pager Scroll
             LaunchedEffect(selectedCategory) {
                 val index = categories.indexOf(selectedCategory)
                 if (index >= 0 && pagerState.currentPage != index) {
                     pagerState.animateScrollToPage(index)
                 }
             }

             // Sync: Pager Swipe -> Chip Selection
             LaunchedEffect(pagerState.currentPage) {
                 val category = categories[pagerState.currentPage]
                 if (selectedCategory != category) {
                     selectedCategory = category
                 }
             }

             // Swipeable Feed Content
             HorizontalPager(
                 state = pagerState,
                 modifier = Modifier.fillMaxSize()
             ) { page ->
                 // We need to fetch/filter articles for *this* page's category
                 // Optimization: Note that 'articles' state currently holds *only* SelectedCategory's articles
                 // To support true Pager, we strictly need to cache lists for *each* category or fetch on swipe.
                 // For now, to keep it simple & fast: 
                 // We will trigger the fetch when the page settles (via the LaunchedEffect above)
                 // And show Loading/Content for the *current* selection.
                 // Visually, adjacent pages might look empty/loading until settled.
                 // Ideally we'd have a Map<Category, List<Article>>.
                 
                 // For v1 of Swipe: Just show the feed if it matches the page, or a loader.
                 if (categories[page] == selectedCategory) {
                     Box(modifier = Modifier.fillMaxSize()) {
                         if (articles.isEmpty() && isLoading) {
                             // Skeleton Loading State
                             LazyColumn(contentPadding = PaddingValues(16.dp)) {
                                 items(6) {
                                     NewsCardSkeleton()
                                     Spacer(modifier = Modifier.height(12.dp))
                                 }
                             }
                         } else if (articles.isEmpty()) {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No articles found") }
                         } else {
                             LazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
                                 items(articles) { article ->
                                     NewsCard(article, onClick = { selectedArticle = article })
                                     Spacer(modifier = Modifier.height(12.dp))
                                 }
                                 if (isLoading && hasMore) { item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } } }
                             }
                         }
                         
                         // Smart Detail Sheet
                         if (selectedArticle != null) {
                             var isBookmarked by remember { mutableStateOf(repository.isBookmarked(selectedArticle!!.id)) }
                             
                             NewsDetailSheet(
                                 article = selectedArticle!!,
                                 isBookmarked = isBookmarked,
                                 onToggleBookmark = {
                                     repository.toggleBookmark(selectedArticle!!)
                                     isBookmarked = !isBookmarked // Toggle UI instantly
                                 },
                                 onReadMore = { link -> 
                                     repository.addToHistory(selectedArticle!!) // Track History on Deep Dive
                                     uriHandler.openUri(link)
                                 },
                                 onDismiss = { selectedArticle = null }
                             )
                         }
                     }
                 } else {
                    // Off-screen pages (Skeleton placeholder)
                    Box(modifier = Modifier.fillMaxSize()) {
                         LazyColumn(contentPadding = PaddingValues(16.dp)) {
                             items(3) {
                                 NewsCardSkeleton()
                                 Spacer(modifier = Modifier.height(12.dp))
                             }
                         }
                    }
                 }
             }
         }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsCard(article: Article, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(article.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(" • ${article.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" • ${getRelativeTime(article.published)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(Modifier.weight(1f))
                
                // Feature 5: Trust Score Display
                if (article.trustScore != null) {
                    val color = if (article.trustScore >= 80) Color(0xFF4CAF50) else if (article.trustScore >= 50) Color(0xFFFFC107) else Color(0xFFF44336)
                    Surface(
                         color = color.copy(alpha=0.15f), 
                         shape = RoundedCornerShape(50), 
                         border = BorderStroke(1.dp, color.copy(alpha=0.3f))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                text = "★ ${article.trustScore}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            
            val displaySummary = if (article.aiSummary != null && !article.aiSummary.contains("Analysis Failed")) article.aiSummary else article.summary
            Text(HtmlTextMapper.fromHtml(displaySummary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            
            if (article.trustBadge.isNotEmpty() && article.trustBadge != "News") {
                 Spacer(modifier = Modifier.height(12.dp))
                 SuggestionChip(onClick = {}, label = { Text(article.trustBadge) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: (Set<String>) -> Unit) {
    val options = listOf(
        "India News", "World News", "Business", "Technology", "Science", "Health", 
        "Politics", "Entertainment", "Sports", 
        "AI & Frontiers", "Cybersecurity"
    )
    
    // Default: Select ALL categories so user doesn't miss anything (User Request)
    val defaultSelection = remember {
        mutableStateListOf(*options.toTypedArray())
    }
    
    val selected = defaultSelection

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Customize your Feed", 
            style = MaterialTheme.typography.headlineLarge, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Select topics you care about.", 
            style = MaterialTheme.typography.bodyLarge, 
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(options) { option ->
                val isSelected = selected.contains(option)
                FilterChip(
                    selected = isSelected,
                    onClick = { if (isSelected) selected.remove(option) else selected.add(option) },
                    label = { Text(option) },
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onComplete(selected.toSet()) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Get Started", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: DataRepository, 
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    currentRegionState: String,
    onRegionChange: (String) -> Unit
) {
    var watchlistKeyword by remember { mutableStateOf("") }
    // State: Bookmarks and History are local triggers for now
    var bookmarks by remember { mutableStateOf(repository.getBookmarks()) }
    var history by remember { mutableStateOf(repository.getHistory()) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Helpers
    @Composable
    fun SectionHeader(title: String) {
        Text(
            title.uppercase(), 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, 
            modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 12.dp), // Increased spacing
            letterSpacing = 2.sp // Technical spacing
        )
    }

    Scaffold(
        containerColor = Color(0xFF121212), // Deep Matte Black
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SETTINGS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }, 
                navigationIcon = { 
                    IconButton(onClick = onBack) { 
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) 
                    } 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) { // Enable scroll for smaller screens
            
            // 1. WATCHLIST
            SectionHeader("Intelligence Alerts")
            Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                OutlinedTextField(
                    value = watchlistKeyword,
                    onValueChange = { watchlistKeyword = it },
                    label = { Text("Add Keyword", color = Color.Gray) },
                    placeholder = { Text("e.g. NVIDIA", color = Color.DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (watchlistKeyword.isNotEmpty()) {
                            IconButton(onClick = {
                                scope.launch {
                                    repository.addWatchlistKeyword(watchlistKeyword)
                                    android.widget.Toast.makeText(context, "Alert added for '$watchlistKeyword'", android.widget.Toast.LENGTH_SHORT).show()
                                    watchlistKeyword = ""
                                }
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1A1A1A), // Slightly lighter than bg
                        unfocusedContainerColor = Color(0xFF1A1A1A),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF333333), // Subtle border
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp) // Tighter corners
                )
            }
            
            // 2. REGION (Custom Segmented Control)
            SectionHeader("Edition")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                listOf("Global", "India", "USA").forEach { region ->
                    val isSelected = currentRegionState == region
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha=0.15f) else Color.Transparent)
                            .clickable { 
                                onRegionChange(region)
                                repository.saveRegion(region)
                                // Trigger refresh via state
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            region, 
                            fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if(isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // Vertical divider
                    if (region != "USA") {
                        Divider(modifier = Modifier.width(1.dp).height(24.dp).align(Alignment.CenterVertically), color = Color(0xFF333333))
                    }
                }
            }

            // 3. BOOKMARKS
            if (bookmarks.isNotEmpty()) {
                SectionHeader("Saved Briefs")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(bookmarks) { article ->
                        Card(
                            modifier = Modifier.width(220.dp).height(120.dp), // Slightly wider
                            shape = RoundedCornerShape(8.dp),
                            onClick = { onOpenUrl(article.link) },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                            border = BorderStroke(0.5.dp, Color(0xFF333333))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        article.title, 
                                        maxLines = 3, 
                                        overflow = TextOverflow.Ellipsis, 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        color = Color.White,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(6.dp))
                                        Text(article.source, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                                // Delete Button (Top Right)
                                IconButton(
                                    onClick = { 
                                        repository.removeBookmark(article.id)
                                        bookmarks = repository.getBookmarks() // Refresh State
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            
            // 4. HISTORY
            if (history.isNotEmpty()) {
                SectionHeader("Recent Access")
                Column(modifier = Modifier.padding(horizontal = 8.dp)) { // Use Column inside Scrollable Column
                    history.take(8).forEach { article -> // Limit visually
                        ListItem(
                             modifier = Modifier.clickable { onOpenUrl(article.link) },
                             colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                             headlineContent = { 
                                 Text(
                                     article.title, 
                                     maxLines = 1, 
                                     overflow = TextOverflow.Ellipsis, 
                                     color = Color.LightGray, 
                                     style = MaterialTheme.typography.bodyMedium
                                 ) 
                             },
                             leadingContent = { 
                                 Icon(
                                     Icons.Filled.DateRange, 
                                     contentDescription = null, 
                                     tint = Color.DarkGray,
                                     modifier = Modifier.size(18.dp)
                                 ) 
                             },
                             trailingContent = {
                                 Text(
                                     getRelativeTime(article.published).replace(" ago", ""), // Shorten
                                     style = MaterialTheme.typography.labelSmall,
                                     color = Color(0xFF444444) // Subtler
                                 )
                             }
                        )
                        Divider(color = Color(0xFF1A1A1A), thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            
            Spacer(Modifier.height(48.dp))
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "BRIEF. v${BuildConfig.VERSION_NAME}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = Color.DarkGray,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
fun NewsCardSkeleton() {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(width = 80.dp, height = 16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(width = 60.dp, height = 16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.size(width = 40.dp, height = 20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Box(modifier = Modifier.fillMaxWidth(0.9f).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary lines
            Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(0.8f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width,
        targetValue = 2 * size.width,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000)
        ),
        label = "shimmer"
    )

    background(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                Color.LightGray.copy(alpha = 0.6f),
                Color.LightGray.copy(alpha = 0.2f),
                Color.LightGray.copy(alpha = 0.6f)
            ),
            start = androidx.compose.ui.geometry.Offset(startOffsetX, 0f),
            end = androidx.compose.ui.geometry.Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size.toSize()
    }
}
