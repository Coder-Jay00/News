package com.intelligence.brief

import android.Manifest
import android.content.Intent
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
import java.util.*

fun getRelativeTime(publishedAt: String): String {
    if (publishedAt.isNullOrEmpty()) return ""
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    
    return try {
        val cleanDate = publishedAt
            .replace("Z", "")
            .split("+")[0]
            .trim()

        var date: Date? = null
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                date = sdf.parse(cleanDate)
                if (date != null) break
            } catch (e: Exception) { continue }
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
        
        // Background sync (as fallback)
        NewsSyncWorker.schedule(this)


        setContent {
            // Theme state: null = follow device system theme (default behavior)
            var isDarkMode by remember { mutableStateOf<Boolean?>(null) }
            
            // Compute effective theme (null = follow system automatically)
            val useDarkTheme = isDarkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
            
            BriefTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigator(
                        repository = repository,
                        isDarkMode = isDarkMode,
                        onToggleTheme = { isDarkMode = if (isDarkMode == true) false else true }
                    )
                }
            }
        }
        
        // Check for Updates
        val updateManager = UpdateManager(this)
        lifecycleScope.launch {
            val updateUrl = updateManager.checkForUpdate()
            if (updateUrl != null) {
                updateManager.triggerUpdate(updateUrl) 
            }
        }

        // Handle incoming URL from notifications (Deep-linking)
        intent.getStringExtra("url")?.let { url ->
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(browserIntent)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to open deep-link URL: $url", e)
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationHelper.hasNotificationPermission(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
                    Text(
                        "Brief.", 
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    ) 
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = article.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• ${article.category}", 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val timeAgo = getRelativeTime(article.published)
                if (timeAgo.isNotEmpty()) {
                    Text(
                        text = " • $timeAgo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                !article.aiSummary.contains("Analysis Failed", ignoreCase = true) &&
                !article.aiSummary.contains("Unavailable", ignoreCase = true)) {
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
