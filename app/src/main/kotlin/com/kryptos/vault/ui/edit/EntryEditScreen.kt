package com.kryptos.vault.ui.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.VaultEntry
import com.kryptos.vault.data.templateFromShareId
import com.kryptos.vault.ui.VaultViewModel
import com.kryptos.vault.ui.components.NativeAdCard
import com.kryptos.vault.ui.scan.QrPayloadType
import com.kryptos.vault.ui.scan.QrPayloads
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.fmz.kryptos.R

object ScanResultKeys {
    const val PARSED_FIELDS_JSON = "scan_parsed_fields_json"
    const val RAW_TEXT = "scan_raw_text"
    const val ATTACHMENT = "scan_attachment"
    const val NFC_PREFILL_FIELDS_JSON = "nfc_prefill_fields_json"
    const val PREFILL_TEMPLATE = "prefill_template"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditScreen(
    id: Long,
    viewModel: VaultViewModel,
    onDone: () -> Unit,
    onScan: (Template) -> Unit,
    onQrScan: () -> Unit = {},
    onNfcScan: (Template, String) -> Unit = { _, _ -> },
    savedStateHandle: SavedStateHandle? = null,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var loaded by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var titleError by rememberSaveable { mutableStateOf(false) }
    var template by rememberSaveable(stateSaver = TemplateSaver) { mutableStateOf(Template.ID_CARD) }
    val fields = rememberSaveable(saver = FieldsListSaver) { mutableStateListOf<Pair<String, String>>() }
    var existingAttachment by remember { mutableStateOf<ByteArray?>(null) }
    var existingCreatedAt by remember { mutableStateOf<Long?>(null) }
    var duplicateToConfirm by remember { mutableStateOf<VaultEntry?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val adsRemoved by viewModel.adsRemoved.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var datePickerTargetIndex by remember { mutableStateOf<Int?>(null) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val titleFocusRequester = remember { FocusRequester() }

    fun doSave(force: Boolean = false) {
        if (isSaving) return
        
        if (title.isBlank()) {
            titleError = true
            scope.launch {
                val titleIndex = if (supportsImportPanel(template)) 3 else 2
                listState.animateScrollToItem(titleIndex)
                titleFocusRequester.requestFocus()
                snackbarHostState.showSnackbar("Please enter a title.")
            }
            return
        }
        titleError = false

        val savableFields = fieldsForSave(template, fields.toList())
        val entry = VaultEntry(
            id = id,
            template = template,
            title = title,
            fieldsJson = FieldsCodec.encode(savableFields),
            attachment = existingAttachment,
            createdAt = existingCreatedAt ?: System.currentTimeMillis(),
        )

        isSaving = true

        if (!force) {
            val dup = viewModel.findDuplicate(entry)
            if (dup != null) {
                duplicateToConfirm = dup
                isSaving = false
                return
            }
        }

        scope.launch {
            try {
                kotlinx.coroutines.withTimeout(10000) {
                    viewModel.upsert(entry)
                }
                onDone()
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Error: ${t.localizedMessage ?: "Failed to save"}")
                isSaving = false
            }
        }
    }

    LaunchedEffect(id) {
        if (loaded) return@LaunchedEffect
        
        val prefillTemplate = savedStateHandle?.get<String>(ScanResultKeys.PREFILL_TEMPLATE)
            ?.let { runCatching { Template.valueOf(it) }.getOrNull() }

        if (id != 0L) {
            viewModel.get(id)?.let { e ->
                title = e.title
                template = e.template
                fields.clear()
                val decoded = FieldsCodec.decode(e.fieldsJson).map { (k, v) ->
                    val isExpiry = e.template == Template.PAYMENT_CARD && (k.contains("expiry", ignoreCase = true) || k.contains("expires", ignoreCase = true))
                    k to if (isExpiry) v.filter { it.isDigit() }.take(4) else v
                }
                fields.addAll(decoded)
                existingAttachment = e.attachment
                existingCreatedAt = e.createdAt
            }
        } else {
            if (prefillTemplate != null) {
                template = prefillTemplate
            }
            if (fields.isEmpty()) {
                defaultFieldsFor(template).forEach { fields.add(it to "") }
            }
        }
        loaded = true
        savedStateHandle?.remove<String>(ScanResultKeys.PREFILL_TEMPLATE)
    }

    val parsedJsonState = savedStateHandle
        ?.getStateFlow<String?>(ScanResultKeys.PARSED_FIELDS_JSON, null)
        ?.collectAsState()
    LaunchedEffect(parsedJsonState?.value, loaded) {
        if (!loaded) return@LaunchedEffect
        val handle = savedStateHandle ?: return@LaunchedEffect
        val parsedJson: String? = handle[ScanResultKeys.PARSED_FIELDS_JSON]
        val rawText: String? = handle[ScanResultKeys.RAW_TEXT]
        val attachment: ByteArray? = handle[ScanResultKeys.ATTACHMENT]
        if (parsedJson == null && rawText == null && attachment == null) return@LaunchedEffect
        val parsed = parsedJson?.let { FieldsCodec.decode(it) }.orEmpty()
        if (parsed.isNotEmpty()) {
            parsed.forEach { (key, value) ->
                if (value.isBlank()) return@forEach
                val isExpiry = template == Template.PAYMENT_CARD && (key.contains("expiry", ignoreCase = true) || key.contains("expires", ignoreCase = true))
                val sanitizedValue = if (isExpiry) value.filter { it.isDigit() }.take(4) else value
                
                val idx = fields.indexOfFirst { it.first.equals(key, ignoreCase = true) }
                if (idx >= 0) fields[idx] = fields[idx].first to sanitizedValue
                else fields.add(key to sanitizedValue)
            }
        } else if (!rawText.isNullOrBlank()) {
            val sharedEntry = runCatching {
                val json = JSONObject(rawText)
                val isSharedPayload = json.optInt("kryptos") == 1 || json.optString("type") == "kryptos_entry"
                if (!isSharedPayload) return@runCatching null
                val sharedTemplate = templateFromShareId(json.optString("template")) ?: return@runCatching null
                val sharedFields = json.optJSONObject("fields") ?: return@runCatching null
                val map = mutableListOf<Pair<String, String>>()
                val keys = sharedFields.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map.add(key to sharedFields.get(key).toString())
                }
                Triple(sharedTemplate, json.optString("title"), map)
            }.getOrNull()

            val qrParsed = if (sharedEntry == null) {
                runCatching {
                    val json = JSONObject(rawText)
                    val map = mutableListOf<Pair<String, String>>()
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map.add(key to json.get(key).toString())
                    }
                    map
                }.getOrNull()
            } else {
                null
            }

            if (sharedEntry != null) {
                val (sharedTemplate, sharedTitle, sharedFields) = sharedEntry
                template = sharedTemplate
                if (sharedTitle.isNotBlank()) title = sharedTitle
                fields.clear()
                defaultFieldsFor(sharedTemplate).forEach { fields.add(it to "") }
                sharedFields.forEach { (key, value) ->
                    val isExpiry = sharedTemplate == Template.PAYMENT_CARD && (key.contains("expiry", ignoreCase = true) || key.contains("expires", ignoreCase = true))
                    val sanitizedValue = if (isExpiry) value.filter { it.isDigit() }.take(4) else value

                    val idx = fields.indexOfFirst { it.first.equals(key, ignoreCase = true) }
                    if (idx >= 0) fields[idx] = fields[idx].first to sanitizedValue
                    else fields.add(key to sanitizedValue)
                }
            } else if (qrParsed != null) {
                qrParsed.forEach { (key, value) ->
                    val idx = fields.indexOfFirst { it.first.equals(key, ignoreCase = true) }
                    if (idx >= 0) fields[idx] = fields[idx].first to value
                    else fields.add(key to value)
                }
            } else {
                val targetField = if (template == Template.QR_CODE) "Data" else "Scanned text"
                val idx = fields.indexOfFirst { it.first.equals(targetField, ignoreCase = true) }
                if (idx >= 0) fields[idx] = fields[idx].first to rawText.trim()
                else fields.add(targetField to rawText.trim())
            }
        }
        if (attachment != null) existingAttachment = attachment
        handle.remove<String>(ScanResultKeys.PARSED_FIELDS_JSON)
        handle.remove<String>(ScanResultKeys.RAW_TEXT)
        handle.remove<ByteArray>(ScanResultKeys.ATTACHMENT)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "New entry" else "Edit entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        TextButton(onClick = { doSave() }) {
                            Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Template.entries.forEach { t ->
                        FilterChip(
                            selected = template == t,
                            onClick = {
                                template = t
                                if (id == 0L && fields.all { it.second.isBlank() }) {
                                    fields.clear()
                                    defaultFieldsFor(t).forEach { fields.add(it to "") }
                                }
                            },
                            label = { Text(prettyTemplate(t)) },
                            leadingIcon = {
                                Icon(
                                    templateIcon(t),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            if (supportsImportPanel(template)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                if (template == Template.QR_CODE) "Import QR data" else "Fill or import ${prettyTemplate(template).lowercase()}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                when (template) {
                                    Template.PASSPORT -> "Scan the photo page with your camera, or read the chip over NFC for the most accurate result."
                                    Template.PAYMENT_CARD -> "Scan the card with your camera, or use NFC to securely read the card number and expiry directly from the chip. Note: Camera scan works best for embossed cards; for modern flat cards, use NFC scan instead."
                                    Template.QR_CODE -> "Scan an existing QR code to import its content and use this entry as a duplicator."
                                    Template.API_KEY,
                                    Template.NOTE -> "Scan a shared Kryptos entry from another device to fill this ${prettyTemplate(template).lowercase()}."
                                    else -> "Use your camera to scan the document for auto-fill, or scan a shared Kryptos entry from another device."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (supportsCameraScan(template)) {
                                    Button(
                                        onClick = { onScan(template) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DocumentScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.scan_document))
                                    }
                                }
                                if (supportsNfcScan(template)) {
                                    FilledTonalButton(
                                        onClick = {
                                            onNfcScan(template, FieldsCodec.encode(fields.toList()))
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Nfc,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.scan_nfc))
                                    }
                                }
                                FilledTonalButton(
                                    onClick = onQrScan,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (template == Template.QR_CODE) "Scan QR code" else "Scan shared entry")
                                }
                            }
                        }
                    }
                }
            }

            if (template == Template.QR_CODE) {
                item {
                    QrTypePanel(
                        selected = QrPayloads.selectedType(fields.toList()),
                        onSelect = { type ->
                            fields.clear()
                            fields.addAll(QrPayloads.defaultFields(type))
                            if (title.isBlank()) title = type.defaultQrTitle()
                        },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        "  Or fill manually  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text(stringResource(R.string.title)) },
                    isError = titleError,
                    supportingText = if (titleError) { { Text(stringResource(R.string.title_is_required)) } } else null,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                )
            }

            items(fields.size) { i ->
                val name = fields[i].first
                val isDefault = defaultFieldsFor(template).any { it.equals(name, ignoreCase = true) }

                EditableFieldRow(
                    name = name,
                    value = fields[i].second,
                    template = template,
                    isDefault = isDefault,
                    onNameChange = { fields[i] = it to fields[i].second },
                    onValueChange = { input ->
                        val currentName = fields[i].first
                        val isExpiry = template == Template.PAYMENT_CARD && (currentName.contains("expiry", ignoreCase = true) || currentName.contains("expires", ignoreCase = true))

                        val filtered = if (isNumericField(currentName, template) || isExpiry) {
                            input.filter { it.isDigit() }.let { if (isExpiry) it.take(4) else it }
                        } else {
                            input
                        }
                        fields[i] = currentName to filtered
                    },
                    onRemove = { fields.removeAt(i) },
                    onPickDate = { datePickerTargetIndex = i },
                )
            }
            item {
                AssistChip(
                    onClick = { fields.add("Field" to "") },
                    label = { Text(stringResource(R.string.add_field)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            if (!adsRemoved) {
                item {
                    NativeAdCard(
                        adUnitId = "ca-app-pub-1016705366714872/4650414807",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        duplicateToConfirm?.let { dup ->
            AlertDialog(
                onDismissRequest = { duplicateToConfirm = null },
                title = { Text(stringResource(R.string.potential_duplicate)) },
                text = {
                    Text(stringResource(R.string.duplicate_entry_warning, dup.title))
                },
                confirmButton = {
                    TextButton(onClick = {
                        duplicateToConfirm = null
                        doSave(force = true)
                    }) { Text(stringResource(R.string.save_anyway)) }
                },
                dismissButton = {
                    TextButton(onClick = { duplicateToConfirm = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        datePickerTargetIndex?.let { index ->
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { datePickerTargetIndex = null },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            val formatted = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            fields[index] = fields[index].first to formatted
                        }
                        datePickerTargetIndex = null
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { datePickerTargetIndex = null }) { Text(stringResource(R.string.cancel)) }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}
