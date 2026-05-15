package com.kryptos.vault.ui.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
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
import com.kryptos.vault.ui.VaultViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var loaded by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var template by rememberSaveable(stateSaver = TemplateSaver) { mutableStateOf(Template.ID_CARD) }
    val fields = rememberSaveable(saver = FieldsListSaver) { mutableStateListOf<Pair<String, String>>() }
    var existingAttachment by remember { mutableStateOf<ByteArray?>(null) }
    var duplicateToConfirm by remember { mutableStateOf<VaultEntry?>(null) }

    var datePickerTargetIndex by remember { mutableStateOf<Int?>(null) }
    val focusManager = LocalFocusManager.current

    fun doSave(force: Boolean = false) {
        val entry = VaultEntry(
            id = id,
            template = template,
            title = title,
            fieldsJson = FieldsCodec.encode(fields.toList()),
            attachment = existingAttachment,
        )
        if (!force) {
            val dup = viewModel.findDuplicate(entry)
            if (dup != null) {
                duplicateToConfirm = dup
                return
            }
        }
        scope.launch {
            viewModel.upsert(entry)
            onDone()
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
            val qrParsed = runCatching {
                val json = JSONObject(rawText)
                val map = mutableListOf<Pair<String, String>>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map.add(key to json.get(key).toString())
                }
                map
            }.getOrNull()

            if (qrParsed != null) {
                qrParsed.forEach { (key, value) ->
                    val idx = fields.indexOfFirst { it.first.equals(key, ignoreCase = true) }
                    if (idx >= 0) fields[idx] = fields[idx].first to value
                    else fields.add(key to value)
                }
            } else {
                val targetField = if (template == Template.QR_CODE) "Content" else "Scanned text"
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
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "New entry" else "Edit entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 12.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { doSave() },
                        enabled = title.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (id == 0L) "Save Entry" else "Save Changes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (!loaded) return@Scaffold

        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp, end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }
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

            if (supportsCameraScan(template) || supportsNfcScan(template) || template == Template.QR_CODE) {
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
                                if (template == Template.QR_CODE) "Import QR data" else "Fill from ${prettyTemplate(template).lowercase()}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                when (template) {
                                    Template.PASSPORT -> "Scan the photo page with your camera, or read the chip over NFC for the most accurate result."
                                    Template.PAYMENT_CARD -> "Scan the card with your camera, or use NFC to securely read the card number and expiry directly from the chip. Note: Camera scan works best for embossed cards; for modern flat cards, use NFC scan instead."
                                    Template.QR_CODE -> "Scan an existing QR code to import its content and use this entry as a duplicator."
                                    else -> "Use your camera to scan the document for auto-fill, or scan a QR code to quickly import data."
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
                                        Text("Scan document")
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
                                        Text("Scan NFC")
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
                                    Text("Scan QR")
                                }
                            }
                        }
                    }
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

            items(fields.size) { i ->
                val name = fields[i].first
                val isDefault = defaultFieldsFor(template).any { it.equals(name, ignoreCase = true) }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isDefault) name.uppercase() else "CUSTOM FIELD",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        IconButton(
                            onClick = { fields.removeAt(i) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove field",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!isDefault) {
                        OutlinedTextField(
                            value = fields[i].first,
                            onValueChange = { fields[i] = it to fields[i].second },
                            label = { Text("Field name") },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    OutlinedTextField(
                        value = fields[i].second,
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
                        label = { if (!isDefault) Text("Value") },
                        shape = RoundedCornerShape(20.dp),
                        trailingIcon = if (isDateField(fields[i].first, template)) {
                            {
                                IconButton(onClick = { datePickerTargetIndex = i }) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                                }
                            }
                        } else null,
                        visualTransformation = if (template == Template.PAYMENT_CARD && (fields[i].first.contains("expiry", ignoreCase = true) || fields[i].first.contains("expires", ignoreCase = true))) {
                            ExpiryVisualTransformation()
                        } else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isNumericField(fields[i].first, template) || (template == Template.PAYMENT_CARD && fields[i].first.contains("expiry", ignoreCase = true))) KeyboardType.Number else KeyboardType.Text,
                            imeAction = if (i < fields.size - 1) ImeAction.Next else ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (it.isFocused && isDateField(fields[i].first, template)) {
                                    datePickerTargetIndex = i
                                    focusManager.clearFocus()
                                }
                            },
                        readOnly = isDateField(fields[i].first, template)
                    )

                    if (isDateField(fields[i].first, template) && fields[i].second.isNotBlank()) {
                        Text(
                            text = "Kryptos will notify you before this expires.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
            item {
                AssistChip(
                    onClick = { fields.add("Field" to "") },
                    label = { Text("Add field") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        duplicateToConfirm?.let { dup ->
            AlertDialog(
                onDismissRequest = { duplicateToConfirm = null },
                title = { Text("Potential duplicate") },
                text = {
                    Text("An entry with similar details already exists (\"${dup.title}\"). Save this anyway?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        duplicateToConfirm = null
                        doSave(force = true)
                    }) { Text("Save anyway") }
                },
                dismissButton = {
                    TextButton(onClick = { duplicateToConfirm = null }) {
                        Text("Cancel")
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
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { datePickerTargetIndex = null }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

private fun templateIcon(t: Template): ImageVector = when (t) {
    Template.ID_CARD -> Icons.Default.Badge
    Template.PASSPORT -> Icons.Default.Public
    Template.DRIVERS_LICENSE -> Icons.Default.DirectionsCar
    Template.BIRTH_CERTIFICATE -> Icons.Default.ChildCare
    Template.PAYMENT_CARD -> Icons.Default.CreditCard
    Template.BANK_ACCOUNT -> Icons.Default.AccountBalance
    Template.TAX_NUMBER -> Icons.Default.Description
    Template.API_KEY -> Icons.Default.VpnKey
    Template.NOTE -> Icons.Default.Description
    Template.QR_CODE -> Icons.Default.QrCode
}

private class ExpiryVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Only format if the input is digits; if slashes leaked in, strip them for the mask logic.
        val digits = text.text.filter { it.isDigit() }.take(4)
        var out = ""
        for (i in digits.indices) {
            out += digits[i]
            if (i == 1 && digits.length > 2) out += "/"
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 2) return offset
                return offset + 1
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 2) return offset
                return offset - 1
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

private fun isDateField(name: String, template: Template): Boolean {
    val n = name.lowercase()
    if (template == Template.PAYMENT_CARD && (n.contains("expiry") || n.contains("expires"))) return false
    return n.contains("date") || n.contains("expiry") || n.contains("expires") || n.contains("dob")
}

private fun isNumericField(name: String, template: Template): Boolean {
    val n = name.lowercase()
    if (template == Template.PAYMENT_CARD && (n.contains("expiry") || n.contains("expires"))) return true
    // Target fields that are strictly numeric in nature
    return n == "number" || n.contains("cvv") || n.contains("pin") || n.contains("cvc") || n == "account number"
}

private fun supportsCameraScan(t: Template): Boolean = when (t) {
    Template.ID_CARD,
    Template.PASSPORT,
    Template.DRIVERS_LICENSE,
    Template.BIRTH_CERTIFICATE,
    Template.PAYMENT_CARD,
    Template.BANK_ACCOUNT,
    Template.TAX_NUMBER -> true
    Template.API_KEY,
    Template.NOTE,
    Template.QR_CODE -> false
}

private fun supportsNfcScan(t: Template): Boolean = t == Template.PASSPORT || t == Template.PAYMENT_CARD

private fun prettyTemplate(t: Template): String = when (t) {
    Template.ID_CARD -> "ID card"
    Template.PASSPORT -> "Passport"
    Template.DRIVERS_LICENSE -> "Driver's license"
    Template.BIRTH_CERTIFICATE -> "Birth certificate"
    Template.PAYMENT_CARD -> "Payment card"
    Template.BANK_ACCOUNT -> "Bank"
    Template.TAX_NUMBER -> "Tax number"
    Template.API_KEY -> "API key"
    Template.NOTE -> "Note"
    Template.QR_CODE -> "QR code"
}

private val TemplateSaver: Saver<Template, String> = Saver(
    save = { it.name },
    restore = { runCatching { Template.valueOf(it) }.getOrDefault(Template.ID_CARD) },
)

private val FieldsListSaver: Saver<SnapshotStateList<Pair<String, String>>, Any> =
    listSaver(
        save = { list -> list.flatMap { listOf(it.first, it.second) } },
        restore = { flat ->
            val list = mutableStateListOf<Pair<String, String>>()
            var i = 0
            while (i + 1 < flat.size) {
                list.add(flat[i].toString() to flat[i + 1].toString())
                i += 2
            }
            list
        },
    )

private fun defaultFieldsFor(template: Template): List<String> = when (template) {
    Template.ID_CARD -> listOf("Full name", "ID number", "Date of birth", "Nationality", "Expiry")
    Template.PASSPORT -> listOf("Surname", "Given names", "Passport number", "Nationality", "Date of birth", "Sex", "Expiry")
    Template.DRIVERS_LICENSE -> listOf("Full name", "License number", "Class", "Date of birth", "Expiry", "Country/State")
    Template.BIRTH_CERTIFICATE -> listOf("Full name", "Date of birth", "Place of birth", "Father's name", "Mother's name", "Registration number", "Date of issue")
    Template.PAYMENT_CARD -> listOf("Issuer", "Cardholder", "Number", "Expiry", "CVV")
    Template.BANK_ACCOUNT -> listOf("Bank", "Account holder", "Account number", "IBAN", "SWIFT/BIC", "PIN")
    Template.TAX_NUMBER -> listOf("Full name", "Tax number", "Country")
    Template.API_KEY -> listOf("Service", "Environment", "Key", "Secret")
    Template.NOTE -> listOf("Content")
    Template.QR_CODE -> listOf("Content")
}
