package com.kryptos.vault.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kryptos.vault.BillingManager
import com.kryptos.vault.KryptosApp
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.VaultEntry
import com.kryptos.vault.ui.VaultViewModel
import com.kryptos.vault.ui.account.AccountSheet
import com.kryptos.vault.ui.cards.HeroCard
import kotlin.math.absoluteValue

private const val STACK_THRESHOLD = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    viewModel: VaultViewModel,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onSignOut: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val ctx = LocalContext.current
    val auth = remember { (ctx.applicationContext as KryptosApp).authManager }
    val photoUrl = auth.currentAccount?.photoUrl
    var showAccount by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    
    val reachedLimit = !isPremium && entries.size >= BillingManager.FREE_ENTRY_LIMIT

    val filtered = remember(entries, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) entries
        else entries.filter { it.matches(q) }
    }

    val grouped = remember(filtered) {
        Template.entries.mapNotNull { t ->
            val es = filtered.filter { it.template == t }
            if (es.isEmpty()) null else t to es
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(45.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.fmz.kryptos.R.drawable.ic_launcher_monochrome),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                },
                title = {
                    Text(
                        "Kryptos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showAccount = true },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(48.dp)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                    ) {
                        if (!photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Account",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = "Account",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(if (reachedLimit) "Unlock Pro" else "Add Entry") },
                icon = { 
                    Icon(
                        if (reachedLimit) Icons.Filled.WorkspacePremium else Icons.Filled.Add, 
                        contentDescription = null
                    ) 
                },
                onClick = { if (reachedLimit) showAccount = true else onAdd() },
                shape = RoundedCornerShape(20.dp),
                containerColor = if (reachedLimit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                contentColor = if (reachedLimit) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search your vault") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            if (entries.isEmpty()) {
                EmptyState(
                    "Your vault is empty.\nTap the button below to secure your first document.",
                    Modifier.weight(1f)
                )
            } else if (filtered.isEmpty()) {
                EmptyState(
                    "No entries match \"$query\".",
                    Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        bottom = 88.dp,
                        top = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    grouped.forEach { (template, items) ->
                        item(key = "header_${template.name}") {
                            CategoryHeader(template = template, count = items.size)
                        }
                        if (items.size > STACK_THRESHOLD) {
                            item(key = "stack_${template.name}") {
                                EntryStack(entries = items, onOpen = onOpen)
                            }
                        } else {
                            items(items, key = { "row_${it.id}" }) { entry ->
                                Box(modifier = Modifier.padding(horizontal = 40.dp)) {
                                    HeroCardTile(entry = entry, onClick = { onOpen(entry.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showAccount) {
            AccountSheet(
                onDismiss = { showAccount = false },
                onSignOut = {
                    showAccount = false
                    onSignOut()
                }
            )
        }
    }
}

@Composable
private fun CategoryHeader(template: Template, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    iconFor(template),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            labelFor(template),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeroCardTile(entry: VaultEntry, onClick: () -> Unit) {
    val fields = remember(entry.fieldsJson) {
        runCatching { FieldsCodec.decode(entry.fieldsJson) }.getOrDefault(emptyList())
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        HeroCard(
            template = entry.template,
            title = entry.title.ifBlank { "Untitled Entry" },
            fields = fields,
            attachment = entry.attachment,
            onCopy = { _, _ -> },
            interactive = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryStack(entries: List<VaultEntry>, onOpen: (Long) -> Unit) {
    val pagerState: PagerState = rememberPagerState(pageCount = { entries.size })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            // Narrower content padding allows more of the neighboring cards to "peek" in
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val rawOffset = pagerState.currentPage - page + pagerState.currentPageOffsetFraction
            val absOffset = rawOffset.absoluteValue.coerceIn(0f, 2f)
            
            // Subtle scale and alpha based on distance from center
            val scale = 1f - (absOffset.coerceAtMost(1f) * 0.12f)
            val alpha = 1f - (absOffset.coerceAtMost(1f) * 0.5f)
            
            // Visual "tuck" effect: neighbors tilt or shift slightly
            val rotation = if (rawOffset > 0) rawOffset * 2f else rawOffset * 2f

            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        rotationZ = rotation
                        // Slight vertical offset for neighbors to create a "depth" look
                        translationY = absOffset * 8.dp.toPx()
                    },
            ) {
                HeroCardTile(entry = entries[page], onClick = { onOpen(entries[page].id) })
            }
        }
        
        if (entries.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.height(8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(entries.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(6.dp)
                    )
                }
            }
        }
    }
}

private fun labelFor(t: Template) = when (t) {
    Template.ID_CARD -> "ID Cards"
    Template.PASSPORT -> "Passports"
    Template.DRIVERS_LICENSE -> "Licenses"
    Template.BIRTH_CERTIFICATE -> "Certificates"
    Template.PAYMENT_CARD -> "Payment Cards"
    Template.BANK_ACCOUNT -> "Bank Accounts"
    Template.TAX_NUMBER -> "Tax Numbers"
    Template.API_KEY -> "API Keys"
    Template.NOTE -> "Notes"
}

private fun iconFor(t: Template): ImageVector = when (t) {
    Template.ID_CARD -> Icons.Filled.Badge
    Template.PASSPORT -> Icons.Filled.Flight
    Template.DRIVERS_LICENSE -> Icons.Filled.DirectionsCar
    Template.BIRTH_CERTIFICATE -> Icons.Filled.Article
    Template.PAYMENT_CARD -> Icons.Filled.CreditCard
    Template.BANK_ACCOUNT -> Icons.Filled.Savings
    Template.TAX_NUMBER -> Icons.Filled.Assignment
    Template.API_KEY -> Icons.Filled.Key
    Template.NOTE -> Icons.Filled.Description
}

private fun VaultEntry.matches(q: String): Boolean {
    if (title.lowercase().contains(q)) return true
    if (labelFor(template).lowercase().contains(q)) return true
    return FieldsCodec.decode(fieldsJson).any { (k, v) ->
        k.lowercase().contains(q) || v.lowercase().contains(q)
    }
}
