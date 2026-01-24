package com.intelligence.brief

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var repository: DataRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = DataRepository(this)

        setContent {
            // Use Material 3 Default Theme (Professional, adjusts to system light/dark)
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigator(repository)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigator(repository: DataRepository) {
    var isOnboarded by remember { mutableStateOf(repository.isOnboarded()) }

    if (isOnboarded) {
        FeedScreen(repository)
    } else {
        OnboardingScreen { interests ->
            repository.saveInterests(interests)
            isOnboarded = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(repository: DataRepository) {
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(true) {
        scope.launch { articles = repository.fetchDailyBrief() }
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (articles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(articles) { article ->
                    NewsCard(article)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsCard(article: Article) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(article.link) } // Click to Open
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Source • Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = article.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Clean up time display if possible, or leave as placeholder
                Text(
                    text = "• ${article.category}", 
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Summary (Cleaned)
            Text(
                text = HtmlTextMapper.fromHtml(article.summary), 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            
            // Trust Badge / Tier (Visual only)
            if (article.trust_badge.isNotEmpty() && article.trust_badge != "Unverified" && article.trust_badge != "News") {
                 Spacer(modifier = Modifier.height(12.dp))
                 SuggestionChip(
                    onClick = { uriHandler.openUri(article.link) }, // Button also opens link
                    label = { Text(article.trust_badge) },
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
    val currentCountry = java.util.Locale.getDefault().country // "IN", "US", etc.
    val defaultSelection = remember {
        val list = mutableListOf<String>()
        if (currentCountry.equals("IN", ignoreCase = true)) list.add("India News")
        list.add("World News") // Always ask/include Global
        mutableStateListOf(*list.toTypedArray())
    }
    
    val selected = defaultSelection

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Customize your Feed", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Select topics you care about.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Use a FlowRow or LazyColumn for many items in valid app, 
        // but for now a simple Column of rows or just wrapping FlowRow if available (experimental).
        // Sticking to Column with scroll for simplicity in this MVP snippet.
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
                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onComplete(selected.toSet()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}
