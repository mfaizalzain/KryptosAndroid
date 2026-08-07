package com.kryptos.vault.ui.cards
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptos.vault.data.Template
import com.kryptos.vault.ui.scan.QrGenerator

/**
 * Set of field keys (lowercased) the hero already renders for [template].
 * The detail screen uses this to avoid duplicating fields below the hero.
 */
fun heroFieldKeys(template: Template): Set<String> = when (template) {
    Template.ID_CARD -> setOf("full name", "id number", "date of birth", "nationality")
    Template.PASSPORT -> setOf("surname", "given names", "passport number", "nationality", "date of birth", "sex", "expiry")
    Template.DRIVERS_LICENSE -> setOf("full name", "license number", "class", "date of birth", "expiry", "country/state")
    Template.BIRTH_CERTIFICATE -> setOf("full name", "date of birth", "place of birth", "father's name", "mother's name", "registration number", "date of issue")
    Template.PAYMENT_CARD -> setOf(
        "cardholder", "number", "card number", "card no", "card no.",
        "expiry", "cvv", "cvc", "security code", "issuer",
    )
    Template.BANK_ACCOUNT -> setOf("bank", "account holder", "account number", "iban", "swift/bic", "pin")
    Template.TAX_NUMBER -> setOf("full name", "tax number", "country")
    Template.API_KEY -> setOf("service", "key", "secret", "environment")
    Template.NOTE -> setOf("content")
    Template.QR_CODE -> setOf("content", "data")
}

@Composable
fun HeroCard(
    template: Template,
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
    onCopy: (label: String, value: String) -> Unit,
    onShare: ((data: String, title: String) -> Unit)? = null,
    interactive: Boolean = true,
) {
    if (interactive) {
        FullHeroCard(
            template = template,
            title = title,
            fields = fields,
            attachment = attachment,
            onCopy = onCopy,
        )
    } else {
        CompactHeroCard(
            template = template,
            title = title,
            fields = fields,
            attachment = attachment,
        )
    }
}

@Composable
fun CompactHeroCard(
    template: Template,
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(compactBackground(template))
            .border(
                1.5.dp, 
                Color.White.copy(alpha = 0.45f), 
                RoundedCornerShape(14.dp)
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
        )

        when (template) {
            Template.PAYMENT_CARD -> CompactPaymentCard(title, fields, attachment)
            Template.API_KEY -> CompactSecretCard(
                template = Template.API_KEY,
                attachment = attachment,
                title = fields.value("Service").ifBlank { title.ifBlank { "API Key" } },
                primaryValue = maskSecret(fields.firstNonBlank("Key", "Secret")),
                secondaryLabel = "Environment",
                secondaryValue = fields.value("Environment"),
            )
            Template.NOTE -> CompactNoteCard(title, fields, attachment)
            Template.QR_CODE -> CompactQrCard(title, fields)
            Template.ID_CARD,
            Template.PASSPORT,
            Template.DRIVERS_LICENSE -> CompactIdentityCard(template, title, fields, attachment)
            else -> CompactDocumentCard(template, title, fields, attachment)
        }
    }
}

@Composable
private fun FullHeroCard(
    template: Template,
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
    onCopy: (String, String) -> Unit,
) {
    val contentColor = heroContentColor(template)
    val labelColor = contentColor.copy(alpha = 0.68f)
    val visibleFields = fields.filter { it.second.isNotBlank() }
    val leadFields = primaryFieldsFor(template)
    val primaryFields = visibleFields
        .filter { field -> leadFields.any { field.first.equals(it, ignoreCase = true) } }
        .ifEmpty { visibleFields.take(3) }
    val additionalFields = visibleFields
        .filterNot { field -> primaryFields.any { it.first.equals(field.first, ignoreCase = true) } }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(compactBackground(template)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FullHeroSlot(template, attachment, fields)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            title.ifBlank { compactFallbackTitle(template) },
                            color = contentColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (template == Template.PAYMENT_CARD) {
                            Icon(
                                Icons.Filled.CreditCard,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    if (primaryFields.isEmpty()) {
                        Text(
                            "No details yet",
                            color = contentColor.copy(alpha = 0.74f),
                            fontSize = 15.sp,
                        )
                    } else {
                        primaryFields.take(if (template == Template.NOTE) 1 else 3).forEach { (name, value) ->
                            FullLabelValue(
                                label = displayLabelFor(template, name),
                                value = displayValueFor(template, name, value),
                                labelColor = labelColor,
                                valueColor = contentColor,
                                maxLines = if (template == Template.NOTE) 6 else 1,
                                mono = shouldUseMono(name),
                                onCopy = { onCopy(name, value) },
                            )
                        }
                    }
                }
            }

            if (additionalFields.isNotEmpty() && template != Template.NOTE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    additionalFields.chunked(2).take(3).forEach { rowFields ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowFields.forEach { (name, value) ->
                                FullLabelValue(
                                    label = displayLabelFor(template, name),
                                    value = displayValueFor(template, name, value),
                                    labelColor = labelColor,
                                    valueColor = contentColor,
                                    mono = shouldUseMono(name),
                                    onCopy = { onCopy(name, value) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowFields.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// --- Helpers ---------------------------------------------------------------

internal fun List<Pair<String, String>>.value(name: String): String =
    firstOrNull { it.first.equals(name, ignoreCase = true) }?.second.orEmpty()

internal fun List<Pair<String, String>>.firstNonBlank(vararg names: String): String {
    for (n in names) {
        val v = value(n)
        if (v.isNotBlank()) return v
    }
    return ""
}

internal val Mono = FontFamily.Monospace

private fun compactBackground(template: Template): Brush = Brush.linearGradient(
    when (template) {
        Template.ID_CARD -> listOf(Color(0xFF0D337F), Color(0xFF1155D8))
        Template.PASSPORT -> listOf(Color(0xFF10162F), Color(0xFF1B356E))
        Template.DRIVERS_LICENSE -> listOf(Color(0xFF0A4D73), Color(0xFF1B8CA6))
        Template.BIRTH_CERTIFICATE -> listOf(Color(0xFF0D5A50), Color(0xFF2F8B73))
        Template.PAYMENT_CARD -> listOf(Color(0xFF151424), Color(0xFF2E174F), Color(0xFF65306F))
        Template.BANK_ACCOUNT -> listOf(Color(0xFF0D5264), Color(0xFF17777A))
        Template.TAX_NUMBER -> listOf(Color(0xFF713019), Color(0xFFA6521F))
        Template.API_KEY -> listOf(Color(0xFF141821), Color(0xFF3B4252))
        Template.NOTE -> listOf(Color(0xFFF0B429), Color(0xFFFFD166))
        Template.QR_CODE -> listOf(Color(0xFF145087), Color(0xFF1A91C7))
    },
)

@Composable
private fun FullHeroSlot(
    template: Template,
    attachment: ByteArray?,
    fields: List<Pair<String, String>>,
) {
    if (template == Template.QR_CODE) {
        val data = fields.value("Data").ifBlank { fields.value("Content") }
        var bmp by remember(data) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(data) {
            if (data.isNotBlank()) {
                val generated = withContext(Dispatchers.Default) {
                    QrGenerator.generate(data)
                }
                bmp = generated
            } else {
                bmp = null
            }
        }
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val currentBmp = bmp
            if (currentBmp != null) {
                Image(
                    bitmap = currentBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            } else {
                Icon(
                    Icons.Filled.QrCode,
                    contentDescription = null,
                    tint = Color(0xFF145087),
                    modifier = Modifier.size(54.dp),
                )
            }
        }
    } else {
        var bmp by remember(attachment) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(attachment) {
            if (attachment != null) {
                val decoded = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(attachment, 0, attachment.size)
                }
                bmp = decoded
            } else {
                bmp = null
            }
        }
        Box(
            modifier = Modifier
                .width(92.dp)
                .height(118.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            val currentBmp = bmp
            if (currentBmp != null) {
                Image(
                    bitmap = currentBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = when (template) {
                        Template.PAYMENT_CARD -> Icons.Filled.CreditCard
                        Template.API_KEY -> Icons.Filled.Key
                        else -> Icons.Filled.Description
                    },
                    contentDescription = null,
                    tint = heroContentColor(template).copy(alpha = 0.78f),
                    modifier = Modifier.size(38.dp),
                )
            }
        }
    }
}

@Composable
private fun FullLabelValue(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    mono: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.then(if (onCopy != null) Modifier.clickable(onClick = onCopy) else Modifier),
    ) {
        Text(
            label.uppercase(),
            color = labelColor,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value.ifBlank { "—" },
            color = valueColor,
            fontSize = 17.sp,
            fontFamily = if (mono) Mono else FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun displayLabelFor(template: Template, name: String): String {
    if (template == Template.PASSPORT && name.equals("Passport number", ignoreCase = true)) return "Number"
    if ((template == Template.ID_CARD || template == Template.DRIVERS_LICENSE) &&
        (name.equals("ID number", ignoreCase = true) || name.equals("License number", ignoreCase = true))
    ) return "Identifier"
    return name
}

private fun displayValueFor(template: Template, name: String, value: String): String {
    val lower = name.lowercase()
    return when {
        template == Template.PAYMENT_CARD && (lower == "number" || lower.contains("card number")) -> maskCardNumber(value)
        lower == "cvv" || lower == "cvc" || lower.contains("secret") || lower == "key" ||
            lower.contains("password") || lower.contains("pin") ||
            lower.contains("account number") || lower == "account no" || lower == "iban" ||
            lower.contains("tax number") || lower == "tax id" -> maskSecret(value)
        template == Template.PAYMENT_CARD && lower.contains("expiry") -> formattedExpiry(value)
        else -> value
    }
}

private fun shouldUseMono(name: String): Boolean {
    val lower = name.lowercase()
    return lower.contains("number") ||
        lower.contains("expiry") ||
        lower.contains("cvv") ||
        lower.contains("cvc") ||
        lower == "key" ||
        lower.contains("secret") ||
        lower.contains("pin") ||
        lower.contains("token")
}

private fun maskSecret(value: String): String =
    if (value.isBlank()) "••••••••••••••••" else "•".repeat(value.length.coerceAtMost(18))

internal fun formattedExpiry(value: String): String {
    val digits = value.filter { it.isDigit() }
    return if (digits.length == 4) "${digits.take(2)}/${digits.takeLast(2)}" else value
}
