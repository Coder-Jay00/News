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
import java.util.TimeZone
import java.util.Locale
import java.util.* 
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
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
    private var updateUrlState = mutableStateOf<String?>(null)
    private var updateVersionState = mutableStateOf<String>("latest")
    private var showUpdateDialogState = mutableStateOf(false)

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == id) {
                // Silent download finished. Now prompt user to install.
                showUpdateDialogState.value = true
                android.widget.Toast.makeText(context, "Update downloaded. Ready to install.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

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
        updateManager = UpdateManager(this)
        
        // Register download receiver with Android 14+ compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                onDownloadComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        checkForUpdates()


        setContent {
            // Theme state: null = follow device system theme (default behavior)
            var isDarkMode by remember { mutableStateOf<Boolean?>(null) }
            
            // Monitor Lifecycle for periodic update checks
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        checkForUpdates()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val showUpdateDialog by remember { showUpdateDialogState }
            val updateUrl by remember { updateUrlState }
            
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
                            onToggleTheme = { isDarkMode = if (isDarkMode == true) false else true }
                        )
                        
                        // Compose-based Update Dialog
                        if (showUpdateDialog && updateUrl != null) {
                            val targetVersion = updateVersionState.value
                            val alreadyDownloaded = updateManager.isUpdateDownloaded(targetVersion)
                            
                            // Feature: Download Progress UX
                            var isDownloading by remember { mutableStateOf(false) }

                            AlertDialog(
                                onDismissRequest = { if (!isDownloading) showUpdateDialogState.value = false },
                                title = { Text(if (isDownloading) "Downloading Update..." else "Update Available") },
                                text = { 
                                    Column {
                                        Text("A new version of Brief. ($targetVersion) is ready.")
                                        if (isDownloading) {
                                            Spacer(Modifier.height(16.dp))
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            Spacer(Modifier.height(8.dp))
                                            Text("Please wait...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        enabled = !isDownloading,
                                        onClick = {
                                            if (alreadyDownloaded) {
                                                installApk(targetVersion)
                                                showUpdateDialogState.value = false
                                            } else {
                                                isDownloading = true
                                                downloadId = updateManager.triggerUpdate(updateUrl!!, targetVersion)
                                                // Keep dialog open to show progress
                                            }
                                        }
                                    ) {
                                        Text(if (alreadyDownloaded) "Install" else if (isDownloading) "Downloading..." else "Update")
                                    }
                                },
                                dismissButton = {
                                    if (!isDownloading) {
                                        TextButton(onClick = { showUpdateDialogState.value = false }) {
                                            Text("Later")
                                        }
                                    }
                                }
                            )
                        }
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
                         
        if (isUpdateUrl) {
            // Extract version from URL if possible (e.g. from GitHub tag)
            val extractedVersion = if (url.contains("/v")) {
                url.substringAfterLast("/v").substringBefore("/")
            } else {
                "latest"
            }
            
            // Normalize to a direct APK link if it's just the homepage or a release page
            val finalUrl = if (!cleanUrl.endsWith(".apk")) {
                "https://github.com/Coder-Jay00/News/releases/latest/download/Brief.apk"
            } else {
                url // Keep original case for actual download
            }
            
            updateVersionState.value = extractedVersion
            updateUrlState.value = finalUrl
            showUpdateDialogState.value = true
        } else {
            // Normal browsing - ONLY redirect if it's NOT a project link we should have handled
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(browserIntent)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Browser redirect failed", e)
            }
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            val updateUrl = updateManager.checkForUpdate()
            if (updateUrl != null) {
                // For auto-check, we don't know the exact version yet, but we'll try to get it from the URL
                val version = if (updateUrl.contains("/download/v")) {
                    updateUrl.substringAfter("/download/v").substringBefore("/")
                } else if (updateUrl.contains("/tags/v")) {
                    updateUrl.substringAfter("/tags/v").substringBefore("/")
                } else {
                    "v1.2.6" // Default to current intended version if check fails
                }
                
                updateVersionState.value = version
                updateUrlState.value = updateUrl
                if (updateManager.isUpdateDownloaded(version)) {
                    // Already downloaded -> Prompt to Install
                    showUpdateDialogState.value = true
                } else {
                    // Not downloaded -> Silent Download
                    downloadId = updateManager.triggerUpdate(updateUrl, version)
                    android.util.Log.d("Update", "Silently starting download for $version")
                    // Do NOT show dialog yet
                }
        }
        }
    }

    private fun installApk(version: String = "latest") {
        val uri = updateManager.getDownloadedFileUri(version)
        if (uri != null) {
            val file = File(uri.path!!)
            try {
                // Check if we can install unknown apps (Android 8.0+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!packageManager.canRequestPackageInstalls()) {
                        val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(settingsIntent)
                        return
                    }
                }

                // For Android 7.0+ we MUST use FileProvider
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    file
                )
                
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                }
                startActivity(installIntent)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Installation failed", e)
                android.widget.Toast.makeText(this, "Installation failed. Please try manual install from downloads.", android.widget.Toast.LENGTH_LONG).show()
                // REMOVED browser redirect fallback to prevent redirection loop
            }
        } else {
            android.widget.Toast.makeText(this, "Update file not found. Re-downloading...", android.widget.Toast.LENGTH_SHORT).show()
            checkForUpdates()
        }
    }

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
        try {
            unregisterReceiver(onDownloadComplete)
        } catch (e: Exception) {
            // Already unregistered or not registered
        }
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
    onToggleTheme: () -> Unit
) {
    var isOnboarded by remember { mutableStateOf(repository.isOnboarded()) }
    var currentScreen by remember { mutableStateOf("feed") } // simple router

    when (currentScreen) {
        "settings" -> {
            SettingsScreen(
                repository = repository,
                onBack = { currentScreen = "feed" }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    repository: DataRepository,
    isDarkMode: Boolean?,
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
    LaunchedEffect(selectedCategory) {
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

             val pullToRefreshState = rememberPullToRefreshState()
            
             LaunchedEffect(pullToRefreshState.isRefreshing) {
                 if (pullToRefreshState.isRefreshing) {
                     refresh() // Suspends until done
                     pullToRefreshState.endRefresh()
                 }
             }
    
             Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
                 if (articles.isEmpty() && isLoading) {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                 } else if (articles.isEmpty()) {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No articles found\nPull down to refresh") }
                 } else {
                     LazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
                         items(articles) { article ->
                             NewsCard(article)
                             Spacer(modifier = Modifier.height(12.dp))
                         }
                         if (isLoading && hasMore) { item { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } } }
                     }
                 }
                 PullToRefreshContainer(
                     state = pullToRefreshState, 
                     modifier = Modifier.align(Alignment.TopCenter),
                     containerColor = MaterialTheme.colorScheme.surface,
                     contentColor = MaterialTheme.colorScheme.primary
                 )
             }
         }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsCard(article: Article) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(article.link) }
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
    onBack: () -> Unit
) {
    val allCategories = listOf("India News", "World News", "Business", "Technology", "Science", "Health", "Politics", "Entertainment", "Sports", "AI & Frontiers", "Cybersecurity")
    val currentInterests = repository.getInterests()
    val selected = remember { mutableStateListOf(*currentInterests.toTypedArray()) }
    
    // Feature 10: Watchlist UI State
    var watchlistKeyword by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // Watchlist Section - Premium Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.15f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔔", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Watchlist Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Notify me for keywords:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = watchlistKeyword,
                        onValueChange = { watchlistKeyword = it },
                        label = { Text("e.g. NVIDIA, Bitcoin") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (watchlistKeyword.isNotEmpty()) {
                                IconButton(onClick = {
                                    scope.launch {
                                        repository.addWatchlistKeyword(watchlistKeyword)
                                        android.widget.Toast.makeText(context, "Alert added for '$watchlistKeyword'", android.widget.Toast.LENGTH_SHORT).show()
                                        watchlistKeyword = ""
                                    }
                                }) {
                                    Text("ADD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            
            Text(
                "Customize Feed", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
            )
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(allCategories) { category ->
                    val isSelected = selected.contains(category)
                    ListItem(
                        headlineContent = { Text(category, fontWeight = FontWeight.Medium) },
                        trailingContent = {
                            Switch(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(category) else selected.remove(category)
                                    repository.saveInterests(selected.toSet())
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            
            // Footer
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Brief. v${BuildConfig.VERSION_NAME}", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
