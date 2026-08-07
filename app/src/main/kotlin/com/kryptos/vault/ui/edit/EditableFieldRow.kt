package com.kryptos.vault.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptos.vault.data.Template
import com.kryptos.vault.security.PasswordGenerator
import androidx.compose.ui.res.stringResource
import com.fmz.kryptos.R

/**
 * Single editable field row: name (for custom fields), value input with
 * per-template sanitization, expiry formatting, date picker affordance, and
 * removal. Extracted from EntryEditScreen to keep the screen composable thin.
 */
@Composable
internal fun EditableFieldRow(
    name: String,
    value: String,
    template: Template,
    isDefault: Boolean,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    onPickDate: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

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
                onClick = onRemove,
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
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.field_name)) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
        }

        val generate: String? = generateFor(name, template)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { if (!isDefault) Text(stringResource(R.string.value)) },
            shape = RoundedCornerShape(20.dp),
            trailingIcon = when {
                isDateField(name, template) -> {
                    {
                        IconButton(onClick = onPickDate) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                        }
                    }
                }
                generate != null -> {
                    {
                        IconButton(onClick = { onValueChange(generate) }) {
                            Icon(Icons.Default.Casino, contentDescription = "Generate secure value")
                        }
                    }
                }
                else -> null
            },
            visualTransformation = if (template == Template.PAYMENT_CARD && (name.contains("expiry", ignoreCase = true) || name.contains("expires", ignoreCase = true))) {
                ExpiryVisualTransformation()
            } else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumericField(name, template) || (template == Template.PAYMENT_CARD && name.contains("expiry", ignoreCase = true))) KeyboardType.Number else KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (it.isFocused && isDateField(name, template)) {
                        onPickDate()
                        focusManager.clearFocus()
                    }
                },
            readOnly = isDateField(name, template)
        )

        if (isDateField(name, template) && value.isNotBlank()) {
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

private fun generateFor(name: String, template: Template): String? {
    val n = name.lowercase()
    return when {
        n.contains("pin") -> PasswordGenerator.pin()
        template == Template.API_KEY && (n == "key" || n.contains("secret")) -> PasswordGenerator.apiKey()
        listOf("password", "secret", "token", "passphrase", "passcode", "key", "code").any { n.contains(it) } ->
            PasswordGenerator.password()
        else -> null
    }
}
