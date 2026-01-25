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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.intelligence.brief.ui.theme.BriefTheme
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.Locale
import java.util.*
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
                installApk(updateVersionState.value)
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
                            
                            AlertDialog(
                                onDismissRequest = { showUpdateDialogState.value = false },
                                title = { Text("Update Available") },
                                text = { Text("A new version of Brief. ($targetVersion) is ready. Would you like to ${if (alreadyDownloaded) "install" else "download"} it now?") },
                                confirmButton = {
                                    Button(onClick = {
                                        if (alreadyDownloaded) {
                                            installApk(targetVersion)
                                        } else {
                                            downloadId = updateManager.triggerUpdate(updateUrl!!, targetVersion)
                                            android.widget.Toast.makeText(this@MainActivity, "Downloading $targetVersion in background...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        showUpdateDialogState.value = false
                                    }) {
                                        Text(if (alreadyDownloaded) "Install" else "Download")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showUpdateDialogState.value = false }) {
                                        Text("Later")
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
                showUpdateDialogState.value = true
            } else {
                updateManager.deleteOldUpdates()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigator(
    repository: DataRepository,
    isDarkMode: Boolean?,
    onToggleTheme: () -> Unit
) {
    var isOnboarded by remember { mutableStateOf(repository.isOnboarded()) }
    var showSettings by remember { mutableStateOf(false) }

    when {
        showSettings -> {
            SettingsScreen(
                repository = repository,
                onBack = { showSettings = false }
            )
        }
        isOnboarded -> {
            FeedScreen(
                repository = repository,
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
                onOpenSettings = { showSettings = true }
            )
        }
        else -> {
            OnboardingScreen { interests ->
                repository.saveInterests(interests)
                isOnboarded = true
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
    onOpenSettings: () -> Unit
) {
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Refresh function - reloads from scratch
    fun refresh() {
        scope.launch {
            isRefreshing = true
            // Trigger a fresh news crawl in the background
            repository.triggerSync()
            
            currentPage = 0
            articles = repository.fetchArticles(0)
            hasMore = articles.size >= DataRepository.PAGE_SIZE
            isRefreshing = false
        }
    }

    // Initial load
    LaunchedEffect(true) {
        isLoading = true
        articles = repository.fetchArticles(0)
        isLoading = false
        hasMore = articles.size >= DataRepository.PAGE_SIZE
    }

    // Detect scroll to bottom and load more
    LaunchedEffect(listState) {
        snapshotFlow { 
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 3 // Trigger when 3 items from bottom
        }.collect { nearBottom ->
            if (nearBottom && hasMore && !isLoading && !isRefreshing && articles.isNotEmpty()) {
                isLoading = true
                currentPage++
                val moreArticles = repository.fetchArticles(currentPage)
                if (moreArticles.isEmpty()) {
                    hasMore = false
                } else {
                    articles = articles + moreArticles
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
                        Text(
                            "Brief.", 
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "v1.2.7 Stable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    // Settings Button on the left
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙️", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    // Theme Toggle Button
                    IconButton(onClick = onToggleTheme) {
                        Text(
                            text = if (isDarkMode == true) "☀️" else "🌙",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()
        
        // Connect the refreshing state
        LaunchedEffect(pullToRefreshState.isRefreshing) {
            if (pullToRefreshState.isRefreshing) {
                isRefreshing = true
                currentPage = 0
                articles = repository.fetchArticles(0)
                hasMore = articles.size >= DataRepository.PAGE_SIZE
                isRefreshing = false
                pullToRefreshState.endRefresh()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            if (articles.isEmpty() && isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (articles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No articles found\nPull down to refresh", 
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(articles) { article ->
                        NewsCard(article)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    // Loading indicator at bottom
                    if (isLoading && hasMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    
                    // End of list message
                    if (!hasMore && articles.isNotEmpty()) {
                        item {
                            Text(
                                "You're all caught up! ✓",
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Pull refresh indicator at the top
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(article.link) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Source • Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = " • ${article.category}", 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val timeAgo = getRelativeTime(article.published)
                if (timeAgo.isNotEmpty()) {
                    Text(
                        text = " • $timeAgo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Summary (Prioritize AI Summary only if it succeeded)
            val displaySummary = if (article.aiSummary != null && 
                !article.aiSummary.lowercase().contains("analysis failed") &&
                !article.aiSummary.lowercase().contains("unavailable")) {
                article.aiSummary
            } else {
                article.summary
            }
            
            Text(
                text = HtmlTextMapper.fromHtml(displaySummary), 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            
            // Trust Badge
            if (article.trustBadge.isNotEmpty() && article.trustBadge != "Unverified" && article.trustBadge != "News") {
                 Spacer(modifier = Modifier.height(12.dp))
                 SuggestionChip(
                    onClick = { uriHandler.openUri(article.link) },
                    label = { Text(article.trustBadge) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = MaterialTheme.colorScheme.secondary
                    )
                )
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
    
    // Auto-detect Country
    val currentCountry = java.util.Locale.getDefault().country
    val defaultSelection = remember {
        val list = mutableListOf<String>()
        if (currentCountry.equals("IN", ignoreCase = true)) list.add("India News")
        list.add("World News")
        mutableStateListOf(*list.toTypedArray())
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
    val allCategories = listOf(
        "India News", "World News", "Business", "Technology", "Science", "Health", 
        "Politics", "Entertainment", "Sports", "AI & Frontiers", "Cybersecurity"
    )
    
    val currentInterests = repository.getInterests()
    val selected = remember { mutableStateListOf(*currentInterests.toTypedArray()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "📰 News Categories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Select topics you want to see",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(allCategories) { category ->
                    val isSelected = selected.contains(category)
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            if (isSelected) selected.remove(category) 
                            else selected.add(category) 
                        },
                        label = { Text(category) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    repository.saveInterests(selected.toSet())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Save Changes", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
