package com.kryptos.vault.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.kryptos.vault.data.FieldsCodec
import com.kryptos.vault.data.Template
import com.kryptos.vault.data.VaultEntry
import com.kryptos.vault.ui.VaultViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ScanResultKeys {
    const val PARSED_FIELDS_JSON = "scan_parsed_fields_json"
    const val RAW_TEXT = "scan_raw_text"
    const val ATTACHMENT = "scan_attachment"
    const val NFC_PREFILL_FIELDS_JSON = "nfc_prefill_fields_json"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditScreen(
    id: Long,
    viewModel: VaultViewModel,
    onDone: () -> Unit,
    onScan: (Template) -> Unit,
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
        // Don't refetch / overwrite when we come back from a child screen (scan, NFC) — that
        // would discard the user's in-progress edits and the freshly-merged scan results.
        if (loaded) return@LaunchedEffect
        if (id != 0L) {
            viewModel.get(id)?.let { e ->
                title = e.title
                template = e.template
                fields.clear()
                fields.addAll(FieldsCodec.decode(e.fieldsJson))
                existingAttachment = e.attachment
            }
        } else if (fields.isEmpty()) {
            defaultFieldsFor(template).forEach { fields.add(it to "") }
        }
        loaded = true
    }

    // Merge results returned by ScanScreen via SavedStateHandle. The keys are observed as
    // a StateFlow so the merge runs when scan pops back into this screen, then cleared.
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
                val idx = fields.indexOfFirst { it.first.equals(key, ignoreCase = true) }
                if (idx >= 0) fields[idx] = fields[idx].first to value
                else fields.add(key to value)
            }
        } else if (!rawText.isNullOrBlank()) {
            val idx = fields.indexOfFirst { it.first.equals("Scanned text", ignoreCase = true) }
            if (idx >= 0) fields[idx] = fields[idx].first to rawText.trim()
            else fields.add("Scanned text" to rawText.trim())
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
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = { doSave() }) { Text("Save") }
                },
            )
        },
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
                        )
                    }
                }
            }

            // Primary scan actions — surface them right after picking the template,
            // since scanning is the fastest way to populate physical-document entries.
            if (supportsCameraScan(template) || supportsNfcScan(template)) {
                item {
                    Card(
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
                                "Fill from ${prettyTemplate(template).lowercase()}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                when (template) {
                                    Template.PASSPORT -> "Scan the photo page with your camera, or read the chip over NFC for the most accurate result."
                                    Template.PAYMENT_CARD -> "Scan the card with your camera, or use NFC to securely read the card number and expiry directly from the chip."
                                    else -> "Point your camera at the document — the scanner auto-crops and fills the fields below."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (supportsCameraScan(template)) {
                                    AssistChip(
                                        onClick = { onScan(template) },
                                        label = { Text("Scan document") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.DocumentScanner,
                                                contentDescription = null,
                                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                                            )
                                        },
                                    )
                                }
                                if (supportsNfcScan(template)) {
                                    AssistChip(
                                        onClick = {
                                            onNfcScan(template, FieldsCodec.encode(fields.toList()))
                                        },
                                        label = { Text("Scan NFC chip") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Nfc,
                                                contentDescription = null,
                                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
            }

            items(fields.size) { i ->
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fields[i].first,
                            onValueChange = { fields[i] = it to fields[i].second },
                            label = { Text("Field name") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = fields[i].second,
                            onValueChange = { 
                                val filtered = if (isNumericField(fields[i].first)) it.filter { c -> c.isDigit() } else it
                                fields[i] = fields[i].first to filtered 
                            },
                            label = { Text("Value") },
                            trailingIcon = if (isDateField(fields[i].first)) {
                                {
                                    IconButton(onClick = { datePickerTargetIndex = i }) {
                                        Icon(Icons.Filled.CalendarToday, contentDescription = "Pick date")
                                    }
                                }
                            } else null,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (isNumericField(fields[i].first)) KeyboardType.Number else KeyboardType.Text
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (it.isFocused && isDateField(fields[i].first)) {
                                        datePickerTargetIndex = i
                                        focusManager.clearFocus()
                                    }
                                },
                            readOnly = isDateField(fields[i].first)
                        )
                    }
                }
            }
            item {
                AssistChip(
                    onClick = { fields.add("Field" to "") },
                    label = { Text("Add field") },
                )
            }
        }

        duplicateToConfirm?.let { dup ->
            androidx.compose.material3.AlertDialog(
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

private fun isDateField(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("date") || n.contains("expiry") || n.contains("expires") || n.contains("dob")
}

private fun isNumericField(name: String): Boolean {
    val n = name.lowercase()
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
    Template.NOTE -> false
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
    Template.ID_CARD -> listOf("Full name", "ID number", "Date of birth", "Nationality")
    Template.PASSPORT -> listOf("Surname", "Given names", "Passport number", "Nationality", "Date of birth", "Sex", "Expiry")
    Template.DRIVERS_LICENSE -> listOf("Full name", "License number", "Class", "Date of birth", "Expiry", "Country/State")
    Template.BIRTH_CERTIFICATE -> listOf("Full name", "Date of birth", "Place of birth", "Father's name", "Mother's name", "Registration number", "Date of issue")
    Template.PAYMENT_CARD -> listOf("Issuer", "Cardholder", "Number", "Expiry", "CVV")
    Template.BANK_ACCOUNT -> listOf("Bank", "Account holder", "Account number", "IBAN", "SWIFT/BIC", "PIN")
    Template.TAX_NUMBER -> listOf("Full name", "Tax number", "Country")
    Template.API_KEY -> listOf("Service", "Environment", "Key", "Secret")
    Template.NOTE -> listOf("Content")
}
