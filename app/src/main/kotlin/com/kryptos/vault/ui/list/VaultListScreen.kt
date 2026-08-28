package com.kryptos.vault.ui.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kryptos.vault.KryptosApp
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.VaultEntry
import com.kryptos.vault.ui.VaultViewModel
import com.kryptos.vault.ui.account.AccountSheet
import com.kryptos.vault.ui.cards.CompactHeroCard
import com.kryptos.vault.ui.components.NativeAdCard
import com.kryptos.vault.ui.theme.AppShapeChip
import com.kryptos.vault.ui.theme.BrandGold
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import com.fmz.kryptos.R

private const val STACK_THRESHOLD = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    viewModel: VaultViewModel,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onQrScan: () -> Unit,
    onSignOut: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val adsRemoved by viewModel.adsRemoved.collectAsState()
    val ctx = LocalContext.current
    val auth = remember { (ctx.applicationContext as KryptosApp).authManager }
    val photoUrl = auth.currentAccount?.photoUrl
    var showAccount by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf<Template?>(null) }

    val grouped = remember(entries) {
        Template.entries.mapNotNull { t ->
            val es = entries.filter { it.template == t }
            if (es.isEmpty()) null else t to es
        }
    }

    val filtered = remember(entries, query, activeCategory) {
        val q = query.trim().lowercase()
        entries.filter { e ->
            val catOk = activeCategory == null || e.template == activeCategory
            val queryOk = q.isEmpty() || e.matches(q)
            catOk && queryOk
        }
    }

    val expiringSoon = remember(entries) { entries.count { it.isExpiringSoon() } }

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
                            .clip(RoundedCornerShape(14.dp))
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
                    Column {
                        Text(
                            "Kryptos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Encrypted · Zero-knowledge",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onQrScan() }) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan_qr_code)
                        )
                    }
                    IconButton(
                        onClick = { showAccount = true },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(46.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        if (!photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Account",
                                modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = "Account",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.add_entry)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { onAdd() },
                shape = RoundedCornerShape(18.dp),
                containerColor = BrandGold,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Hero summary header
            VaultHeader(entries = entries, grouped = grouped, expiringSoon = expiringSoon)

            SearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (entries.isNotEmpty()) {
                CategoryChips(
                    grouped = grouped,
                    activeCategory = activeCategory,
                    onCategory = { activeCategory = it },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            when {
                entries.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Shield,
                    title = stringResource(com.fmz.kryptos.R.string.vault_empty_title),
                    subtitle = stringResource(com.fmz.kryptos.R.string.vault_empty),
                    modifier = Modifier.weight(1f)
                )
                filtered.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(com.fmz.kryptos.R.string.vault_no_match_title),
                    subtitle = stringResource(com.fmz.kryptos.R.string.vault_no_match, query),
                    modifier = Modifier.weight(1f)
                )
                else -> {
                    val activeGroups = remember(filtered) {
                        Template.entries.mapNotNull { t ->
                            val es = filtered.filter { it.template == t }
                            if (es.isEmpty()) null else t to es
                        }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        activeGroups.forEach { (template, items) ->
                            item(key = "header_" + template.name) {
                                CategoryHeader(template = template, count = items.size)
                            }
                            if (items.size > STACK_THRESHOLD) {
                                item(key = "stack_" + template.name) {
                                    EntryStack(entries = items, onOpen = onOpen)
                                }
                            } else {
                                items(items, key = { "row" + it.id }) { entry ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        HeroCardTile(entry = entry, onClick = { onOpen(entry.id) })
                                    }
                                }
                            }
                        }
                        if (!adsRemoved) {
                            item {
                                NativeAdCard(
                                    adUnitId = "ca-app-pub-1016705366714872/4650414807",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
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
private fun VaultHeader(
    entries: List<VaultEntry>,
    grouped: List<Pair<Template, List<VaultEntry>>>,
    expiringSoon: Int,
) {
    val subtitle = buildString {
        append(entries.size)
        append(if (entries.size == 1) " item" else " items")
        if (grouped.isNotEmpty()) {
            append(" · ")
            append(grouped.size)
            append(" categories")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "My Vault",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expiringSoon > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        expiringSoon.toString() + " expiring",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// --- Search field --------------------------------------------------------
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    grouped: List<Pair<Template, List<VaultEntry>>>,
    activeCategory: Template?,
    onCategory: (Template?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = activeCategory == null,
                onClick = { onCategory(null) },
                label = { Text("All") },
                shape = AppShapeChip,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        items(grouped, key = { it.first.name }) { (template, items) ->
            FilterChip(
                selected = activeCategory == template,
                onClick = { onCategory(if (activeCategory == template) null else template) },
                label = { Text(labelFor(template)) },
                shape = AppShapeChip,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
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
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(11.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    iconFor(template),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(19.dp),
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
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HeroCardTile(entry: VaultEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DefaultHeroCard(entry)
    }
}

@Composable
private fun DefaultHeroCard(entry: VaultEntry) {
    val fields = remember(entry.fieldsJson) {
        runCatching { FieldsCodec.decode(entry.fieldsJson) }.getOrDefault(emptyList())
    }
    CompactHeroCard(
        template = entry.template,
        title = entry.title.ifBlank { "Untitled Entry" },
        fields = fields,
        attachment = entry.attachment,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryStack(entries: List<VaultEntry>, onOpen: (Long) -> Unit) {
    val pagerState: PagerState = rememberPagerState(pageCount = { entries.size })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val rawOffset = pagerState.currentPage - page + pagerState.currentPageOffsetFraction
            val absOffset = rawOffset.absoluteValue.coerceIn(0f, 2f)
            val scale by animateFloatAsState(1f - (absOffset.coerceAtMost(1f) * 0.06f))
            val alpha by animateFloatAsState(1f - (absOffset.coerceAtMost(1f) * 0.45f))
            Box(
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    translationY = absOffset * 6.dp.toPx()
                },
            ) {
                HeroCardTile(entry = entries[page], onClick = { onOpen(entries[page].id) })
            }
        }

        if (entries.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.height(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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
    Template.QR_CODE -> "QR Codes"
}

private fun iconFor(t: Template): ImageVector = when (t) {
    Template.ID_CARD -> Icons.Filled.Badge
    Template.PASSPORT -> Icons.Filled.Flight
    Template.DRIVERS_LICENSE -> Icons.Filled.DirectionsCar
    Template.BIRTH_CERTIFICATE -> Icons.AutoMirrored.Filled.Article
    Template.PAYMENT_CARD -> Icons.Filled.CreditCard
    Template.BANK_ACCOUNT -> Icons.Filled.AccountBalance
    Template.TAX_NUMBER -> Icons.AutoMirrored.Filled.Assignment
    Template.API_KEY -> Icons.Filled.Key
    Template.NOTE -> Icons.Filled.Description
    Template.QR_CODE -> Icons.Filled.QrCode
}

private fun VaultEntry.matches(q: String): Boolean {
    if (title.lowercase().contains(q)) return true
    if (labelFor(template).lowercase().contains(q)) return true
    return FieldsCodec.decode(fieldsJson).any { (k, v) ->
        k.lowercase().contains(q) || v.lowercase().contains(q)
    }
}

/** Best-effort expiry detection for the home-screen "expiring soon" signal. */
private fun VaultEntry.isExpiringSoon(): Boolean {
    val expiry = FieldsCodec.decode(fieldsJson)
        .firstOrNull { it.first.lowercase().contains("expiry") || it.first.lowercase().contains("expires") }
        ?.second?.trim() ?: return false
    if (expiry.isEmpty()) return false
    val formatters = listOf(
        DateTimeFormatter.ofPattern("MM/yy"),
        DateTimeFormatter.ofPattern("MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyMM"),
    )
    val date = formatters.firstNotNullOfOrNull { fmt ->
        runCatching { LocalDate.parse(expiry, fmt) }.getOrNull()
    } ?: runCatching { LocalDate.parse(expiry) }.getOrNull() ?: return false
    val today = LocalDate.now()
    if (date.isBefore(today)) return false
    return ChronoUnit.DAYS.between(today, date) <= 180
}
