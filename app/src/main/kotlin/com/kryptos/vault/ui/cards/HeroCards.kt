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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PermIdentity
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptos.vault.data.Template
import com.kryptos.vault.ui.scan.QrGenerator

// --- Public API ------------------------------------------------------------

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
            .background(compactBackground(template))
            .border(
                1.5.dp, 
                Color.White.copy(alpha = 0.50f), 
                RoundedCornerShape(22.dp)
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

private fun List<Pair<String, String>>.value(name: String): String =
    firstOrNull { it.first.equals(name, ignoreCase = true) }?.second.orEmpty()

private fun List<Pair<String, String>>.firstNonBlank(vararg names: String): String {
    for (n in names) {
        val v = value(n)
        if (v.isNotBlank()) return v
    }
    return ""
}

private val Mono = FontFamily.Monospace

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
private fun CompactIdentityCard(
    template: Template,
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
) {
    val number = fields.firstNonBlank("Passport number", "ID number", "License number")
    val name = fields.firstNonBlank("Full name", "Name")
        .ifBlank {
            listOf(fields.value("Given names"), fields.value("Surname"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PhotoSlot(
            attachment,
            Modifier
                .width(58.dp)
                .fillMaxHeight(),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                title.ifBlank {
                    when (template) {
                        Template.PASSPORT -> "Passport"
                        Template.DRIVERS_LICENSE -> "License"
                        else -> "ID Card"
                    }
                },
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LabelValue(
                label = if (template == Template.PASSPORT) "Number" else "Identifier",
                value = number,
                labelColor = Color.White.copy(alpha = 0.68f),
                valueColor = Color.White,
                valueSp = 17,
            )
            LabelValue(
                label = "Name",
                value = name.ifBlank { title },
                labelColor = Color.White.copy(alpha = 0.68f),
                valueColor = Color.White,
                valueSp = 16,
            )
        }
    }
}

@Composable
private fun CompactDocumentCard(
    template: Template,
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompactIconSlot(template, attachment)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title.ifBlank { compactFallbackTitle(template) },
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val visibleFields = fields.filter { it.second.isNotBlank() }.take(2)
            if (visibleFields.isEmpty()) {
                Text(
                    "No details yet",
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                visibleFields.forEach { (label, value) ->
                    LabelValue(
                        label = label,
                        value = value,
                        labelColor = Color.White.copy(alpha = 0.68f),
                        valueColor = Color.White,
                        valueSp = 16,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPaymentCard(title: String, fields: List<Pair<String, String>>, attachment: ByteArray?) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompactIconSlot(Template.PAYMENT_CARD, attachment)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title.ifBlank { fields.value("Issuer").ifBlank { "Payment Card" } },
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (fields.value("Issuer").isNotBlank() && fields.value("Issuer") != title) {
                        Text(
                            fields.value("Issuer"),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                maskCardNumber(fields.firstNonBlank("Number", "Card number")),
                color = Color.White,
                fontSize = 17.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                LabelValue(
                    label = "Cardholder",
                    value = fields.value("Cardholder"),
                    labelColor = Color.White.copy(alpha = 0.68f),
                    valueColor = Color.White,
                    valueSp = 13,
                )
                Spacer(Modifier.weight(1f))
                LabelValue(
                    label = "Expires",
                    value = formattedExpiry(fields.value("Expiry")),
                    labelColor = Color.White.copy(alpha = 0.68f),
                    valueColor = Color.White,
                    valueSp = 13,
                    mono = true,
                )
            }
        }
    }
}

@Composable
private fun CompactSecretCard(
    template: Template,
    attachment: ByteArray?,
    title: String,
    primaryValue: String,
    secondaryLabel: String,
    secondaryValue: String,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompactIconSlot(template, attachment)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                primaryValue,
                color = Color.White,
                fontSize = 17.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LabelValue(
                label = secondaryLabel,
                value = secondaryValue,
                labelColor = Color.White.copy(alpha = 0.68f),
                valueColor = Color.White,
                valueSp = 14,
            )
        }
    }
}

@Composable
private fun CompactNoteCard(title: String, fields: List<Pair<String, String>>, attachment: ByteArray?) {
    val textColor = heroContentColor(Template.NOTE)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompactIconSlot(Template.NOTE, attachment)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title.ifBlank { "Secure Note" },
                color = textColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                fields.value("Content").ifBlank { "No content" },
                color = textColor.copy(alpha = 0.86f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactQrCard(title: String, fields: List<Pair<String, String>>) {
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
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
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
                        .padding(6.dp),
                )
            } else {
                Icon(
                    Icons.Filled.QrCode,
                    contentDescription = null,
                    tint = Color(0xFF145087),
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title.ifBlank { "QR Code" },
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                data.ifBlank { "No QR data" },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactIconSlot(template: Template, attachment: ByteArray?) {
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
            .width(58.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
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
                    Template.BANK_ACCOUNT -> Icons.Filled.AccountBalance
                    Template.TAX_NUMBER -> Icons.AutoMirrored.Filled.Assignment
                    Template.PAYMENT_CARD -> Icons.Filled.CreditCard
                    Template.API_KEY -> Icons.Filled.Key
                    Template.QR_CODE -> Icons.Filled.QrCode
                    else -> Icons.Filled.Description
                },
                contentDescription = null,
                tint = heroContentColor(template).copy(alpha = 0.78f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private fun compactFallbackTitle(template: Template): String = when (template) {
    Template.ID_CARD -> "ID Card"
    Template.PASSPORT -> "Passport"
    Template.DRIVERS_LICENSE -> "License"
    Template.BIRTH_CERTIFICATE -> "Certificate"
    Template.PAYMENT_CARD -> "Payment Card"
    Template.BANK_ACCOUNT -> "Bank Account"
    Template.TAX_NUMBER -> "Tax Number"
    Template.API_KEY -> "API Key"
    Template.NOTE -> "Secure Note"
    Template.QR_CODE -> "QR Code"
}

private fun heroContentColor(template: Template): Color =
    if (template == Template.NOTE) Color(0xFF3F2B00) else Color.White

private fun primaryFieldsFor(template: Template): List<String> = when (template) {
    Template.ID_CARD -> listOf("ID number", "Full name", "Date of birth")
    Template.PASSPORT -> listOf("Passport number", "Surname", "Given names")
    Template.DRIVERS_LICENSE -> listOf("License number", "Full name", "Expiry")
    Template.BIRTH_CERTIFICATE -> listOf("Full name", "Date of birth", "Registration number")
    Template.PAYMENT_CARD -> listOf("Number", "Card number", "Cardholder", "Expiry")
    Template.BANK_ACCOUNT -> listOf("Bank", "Account holder", "Account number")
    Template.TAX_NUMBER -> listOf("Tax number", "Full name", "Country")
    Template.API_KEY -> listOf("Service", "Environment", "Key")
    Template.NOTE -> listOf("Content")
    Template.QR_CODE -> listOf("Data", "Content")
}

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
        lower == "cvv" || lower == "cvc" || lower.contains("secret") || lower == "key" || lower.contains("password") || lower.contains("pin") -> maskSecret(value)
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

private fun formattedExpiry(value: String): String {
    val digits = value.filter { it.isDigit() }
    return if (digits.length == 4) "${digits.take(2)}/${digits.takeLast(2)}" else value
}

private fun Modifier.mainPageCardFrame(interactive: Boolean): Modifier =
    if (interactive) this else this.aspectRatio(1.586f)

@Composable
private fun PhotoSlot(bytes: ByteArray?, modifier: Modifier) {
    var bmp by remember(bytes) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(bytes) {
        if (bytes != null) {
            val decoded = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            bmp = decoded
        } else {
            bmp = null
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33000000)),
        contentAlignment = Alignment.Center,
    ) {
        val currentBmp = bmp
        if (currentBmp != null) {
            Image(bitmap = currentBmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                imageVector = Icons.Filled.PermIdentity,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun LabelValue(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    valueSp: Int = 14,
    mono: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Column(
        modifier = if (onCopy != null) Modifier.clickable(onClick = onCopy) else Modifier,
    ) {
        Text(
            label.uppercase(),
            color = labelColor,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value.ifBlank { "—" },
            color = valueColor,
            fontSize = valueSp.sp,
            fontFamily = if (mono) Mono else FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- ID Card ---------------------------------------------------------------

@Composable
private fun IdCardHero(
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
    onCopy: (String, String) -> Unit,
    interactive: Boolean,
) {
    val bg = Brush.linearGradient(
        0.0f to Color(0xFF1A237E),
        0.5f to Color(0xFF283593),
        1.0f to Color(0xFF3F51B5)
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
    ) {
        // Subtle watermark / pattern effect
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 800f
                    )
                )
        )

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "IDENTITY CARD" }.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = fields.value("Nationality").ifBlank { "National Identity" },
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.5.sp
                    )
                }
                // Microchip icon/graphic
                Box(
                    Modifier
                        .size(width = 28.dp, height = 20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFCFB53B), Color(0xFFF5E1A4), Color(0xFFCFB53B))
                            )
                        )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Body
            Row(Modifier.fillMaxWidth().weight(1f)) {
                PhotoSlot(
                    attachment,
                    Modifier
                        .width(76.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                )
                
                Spacer(Modifier.width(16.dp))
                
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelValue("FULL NAME", fields.value("Full name"),
                        Color.White.copy(alpha = 0.6f), Color.White, 14)
                    
                    LabelValue("DATE OF BIRTH", fields.value("Date of birth"),
                        Color.White.copy(alpha = 0.6f), Color.White, 13, mono = true)
                }
            }

            // Footer (ID Number)
            val idNo = fields.value("ID number")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(enabled = interactive && idNo.isNotBlank()) {
                        onCopy("ID number", idNo)
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ID NO.",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = idNo.ifBlank { "•••• •••• ••••" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = Mono,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

// --- Passport --------------------------------------------------------------

@Composable
private fun CompactPassportHero(
    title: String,
    fields: List<Pair<String, String>>,
) {
    val bg = Brush.linearGradient(listOf(Color(0xFF1F2A4A), Color(0xFF2C3B66), Color(0xFF18213D)))
    val passportNo = fields.value("Passport number")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(24.dp))
            .background(bg),
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Text(
                fields.value("Nationality").ifBlank { title }.ifBlank { "PASSPORT" }.uppercase(),
                color = Color(0xFFE5C97A),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "PASSPORT",
                color = Color(0xFFE5C97A),
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = 4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            LabelValue(
                "Name",
                listOf(fields.value("Given names"), fields.value("Surname"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { title },
                Color(0xFFE5C97A).copy(alpha = 0.72f),
                Color.White,
                14,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                LabelValue(
                    "Passport no.",
                    passportNo,
                    Color(0xFFE5C97A).copy(alpha = 0.72f),
                    Color.White,
                    13,
                    mono = true,
                )
                Spacer(Modifier.weight(1f))
                LabelValue(
                    "Expiry",
                    fields.value("Expiry"),
                    Color(0xFFE5C97A).copy(alpha = 0.72f),
                    Color.White,
                    13,
                    mono = true,
                )
            }
        }
    }
}

@Composable
private fun PassportHero(
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
    onCopy: (String, String) -> Unit,
    interactive: Boolean,
) {
    val coverColor = Color(0xFF1F2A4A) // Classic deep navy passport cover
    val pageColor = Color(0xFFFAF6E8)  // Warm security paper tone
    
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(pageColor)
            .border(0.5.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
    ) {
        // Top Cover Section
        Column(
            Modifier
                .fillMaxWidth()
                .background(coverColor)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            val nationality = fields.value("Nationality").ifBlank { title.ifBlank { "PASSPORT" } }
            Text(
                text = nationality.uppercase(),
                color = Color(0xFFE5C97A),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "PASSPORT",
                color = Color(0xFFE5C97A),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
        }

        // Main Bio Data Page
        Row(
            Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            PhotoSlot(
                attachment,
                Modifier
                    .width(100.dp)
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Black.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            )
            
            Spacer(Modifier.width(18.dp))
            
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelValue("SURNAME", fields.value("Surname"),
                    Color(0xFF6B5E3B), Color(0xFF1A1404), 15)
                
                LabelValue("GIVEN NAMES", fields.value("Given names"),
                    Color(0xFF6B5E3B), Color(0xFF1A1404), 15)
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabelValue("DOB", fields.value("Date of birth"),
                        Color(0xFF6B5E3B), Color(0xFF1A1404), 11, mono = true)
                    LabelValue("SEX", fields.value("Sex"),
                        Color(0xFF6B5E3B), Color(0xFF1A1404), 11)
                    LabelValue("EXPIRY", fields.value("Expiry"),
                        Color(0xFF6B5E3B), Color(0xFF1A1404), 11, mono = true)
                }
                
                val passportNo = fields.value("Passport number")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE5C97A).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clickable(enabled = interactive && passportNo.isNotBlank()) {
                            onCopy("Passport number", passportNo)
                        }
                ) {
                    LabelValue("PASSPORT NO.", passportNo,
                        Color(0xFF6B5E3B), Color(0xFF1A1404), 14, mono = true)
                }
            }
        }

        // Machine Readable Zone (MRZ)
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.03f))
                .border(width = (0.5).dp, color = Color.Black.copy(alpha = 0.05f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = mrzPreview(fields),
                fontFamily = Mono,
                fontSize = 11.sp,
                color = Color(0xFF1A1404).copy(alpha = 0.6f),
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun mrzPreview(fields: List<Pair<String, String>>): String {
    fun norm(s: String) = s.uppercase().replace(Regex("[^A-Z0-9]"), "<").take(20).padEnd(20, '<')
    val surname = norm(fields.value("Surname"))
    val given = norm(fields.value("Given names"))
    val nat = fields.value("Nationality").take(3).uppercase().padEnd(3, '<')
    val no = fields.value("Passport number").take(9).uppercase().padEnd(9, '<')
    return "P<$nat$surname<<$given\n$no<$nat<<<<<<<<<<<<<<<<<<<<<<<<"
}

// --- Payment Card (Credit/Debit) -------------------------------------------

@Composable
fun PaymentCardHero(
    title: String,
    fields: List<Pair<String, String>>,
    onCopy: (String, String) -> Unit,
    interactive: Boolean,
) {
    var revealNumber by remember { mutableStateOf(false) }
    var revealCvv by remember { mutableStateOf(false) }

    val number = fields.firstNonBlank("Number", "Card number", "Card no", "Card no.")
    val cvv = fields.firstNonBlank("CVV", "CVC", "Security code")
    val expiry = fields.value("Expiry").let { 
        if (it.length == 4 && it.all { c -> c.isDigit() }) "${it.take(2)}/${it.drop(2)}" else it 
    }
    val holder = fields.value("Cardholder")
    val issuer = fields.value("Issuer").ifBlank { title }

    val palette = palettePerIssuer(issuer)
    val bg = Brush.linearGradient(palette)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(24.dp))
            .background(bg),
    ) {
        // Glossy diagonal highlight.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.0f),
                        0.5f to Color.White.copy(alpha = 0.07f),
                        1f to Color.White.copy(alpha = 0.0f),
                    )
                )
        )
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.ifBlank { issuer.ifBlank { "CARD" } },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ChipMark()
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val numberModifier = if (interactive) {
                    Modifier.weight(1f).clickable(enabled = number.isNotBlank()) {
                        onCopy("Card number", number)
                    }
                } else {
                    Modifier.weight(1f)
                }
                Text(
                    when {
                        number.isBlank() -> "Not set"
                        revealNumber && interactive -> formatCardNumber(number)
                        else -> maskCardNumber(number)
                    },
                    color = if (number.isBlank()) Color.White.copy(alpha = 0.5f) else Color.White,
                    fontFamily = Mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp,
                    modifier = numberModifier,
                )
                if (interactive && number.isNotBlank()) {
                    IconButton(onClick = { revealNumber = !revealNumber }) {
                        Icon(
                            if (revealNumber) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                LabelValue("Cardholder", holder,
                    Color.White.copy(alpha = 0.7f), Color.White, 13)
                Spacer(Modifier.weight(1f))
                LabelValue("Expires", expiry,
                    Color.White.copy(alpha = 0.7f), Color.White, 13, mono = true)
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "CVV", color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp, letterSpacing = 1.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val cvvModifier = if (interactive) {
                            Modifier.clickable(enabled = cvv.isNotBlank()) { onCopy("CVV", cvv) }
                        } else {
                            Modifier
                        }
                        Text(
                            when {
                                cvv.isBlank() -> "—"
                                revealCvv && interactive -> cvv
                                else -> "•••"
                            },
                            color = if (cvv.isBlank()) Color.White.copy(alpha = 0.5f) else Color.White,
                            fontFamily = Mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = cvvModifier,
                        )
                        if (interactive && cvv.isNotBlank()) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { revealCvv = !revealCvv },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    if (revealCvv) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipMark() {
    Box(
        Modifier
            .size(width = 36.dp, height = 26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE6C77E), Color(0xFFB8923C), Color(0xFFE6C77E)),
                )
            )
            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
    )
}

private fun maskCardNumber(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    val last4 = digits.takeLast(4).padStart(4, '•')
    return "•••• •••• •••• $last4"
}

private fun formatCardNumber(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return digits.chunked(4).joinToString(" ").ifBlank { "—" }
}

private fun palettePerIssuer(issuer: String): List<Color> {
    val seed = issuer.lowercase()
    return when {
        "visa" in seed -> listOf(Color(0xFF1A2A6C), Color(0xFF2A52BE))
        "master" in seed -> listOf(Color(0xFF8E1B1B), Color(0xFFE05A0F))
        "amex" in seed || "american" in seed -> listOf(Color(0xFF0F4C81), Color(0xFF1D8FE1))
        "discover" in seed -> listOf(Color(0xFFE05A0F), Color(0xFFFFB347))
        else -> listOf(Color(0xFF0F1730), Color(0xFF2E3A66), Color(0xFF1A2238))
    }
}

// --- Bank Account ----------------------------------------------------------

@Composable
private fun BankAccountHero(
    title: String,
    fields: List<Pair<String, String>>,
    onCopy: (String, String) -> Unit,
    interactive: Boolean,
) {
    val bg = Brush.linearGradient(listOf(Color(0xFF0E5F4F), Color(0xFF12876E), Color(0xFF0E5F4F)))
    val iban = fields.value("IBAN")
    val acct = fields.value("Account number")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(24.dp))
            .background(bg),
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Text(
                fields.value("Bank").ifBlank { title }.ifBlank { "BANK ACCOUNT" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                fields.value("Account holder"),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            LabelValue(
                "IBAN", iban,
                Color.White.copy(alpha = 0.7f), Color.White, 16, mono = true,
                onCopy = if (interactive && iban.isNotBlank()) ({ onCopy("IBAN", iban) }) else null,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                LabelValue(
                    "Account", acct,
                    Color.White.copy(alpha = 0.7f), Color.White, 13, mono = true,
                    onCopy = if (interactive && acct.isNotBlank()) ({ onCopy("Account number", acct) }) else null,
                )
                Spacer(Modifier.weight(1f))
                if (fields.value("SWIFT/BIC").isNotBlank()) {
                    LabelValue("SWIFT/BIC", fields.value("SWIFT/BIC"),
                        Color.White.copy(alpha = 0.7f), Color.White, 13, mono = true)
                }
            }
        }
    }
}

// --- API Key ---------------------------------------------------------------

@Composable
private fun ApiKeyHero(
    title: String,
    fields: List<Pair<String, String>>,
    onCopy: (String, String) -> Unit,
    interactive: Boolean,
) {
    var revealKey by remember { mutableStateOf(false) }
    var revealSecret by remember { mutableStateOf(false) }
    val key = fields.value("Key")
    val secret = fields.value("Secret")
    val env = fields.value("Environment")
    val service = fields.value("Service")
    
    val displayName = title.ifBlank { service.ifBlank { "API KEY" } }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFEAF2FF),
        modifier = Modifier
            .fillMaxWidth()
            .mainPageCardFrame(interactive)
            .border(1.dp, Color(0xFF7DA7E8).copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    displayName.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (env.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = env.uppercase(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(14.dp))

            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                TerminalLine(
                    prompt = "KEY",
                    value = key,
                    masked = !revealKey || !interactive,
                    onToggle = { revealKey = !revealKey },
                    onCopy = { if (key.isNotBlank()) onCopy("Key", key) },
                    interactive = interactive,
                    accent = MaterialTheme.colorScheme.primary,
                    onSurf = MaterialTheme.colorScheme.onSurface
                )
                if (secret.isNotBlank() || revealSecret) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    TerminalLine(
                        prompt = "SECRET",
                        value = secret,
                        masked = !revealSecret || !interactive,
                        onToggle = { revealSecret = !revealSecret },
                        onCopy = { if (secret.isNotBlank()) onCopy("Secret", secret) },
                        interactive = interactive,
                        accent = MaterialTheme.colorScheme.primary,
                        onSurf = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalLine(
    prompt: String,
    value: String,
    masked: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    interactive: Boolean = true,
    accent: Color,
    onSurf: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            prompt,
            color = accent,
            fontFamily = Mono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(55.dp)
        )
        val valueModifier = if (interactive) {
            Modifier.weight(1f).clickable(enabled = value.isNotBlank(), onClick = onCopy)
        } else {
            Modifier.weight(1f)
        }
        Text(
            text = if (masked) "••••••••••••••••" else value.ifBlank { "—" },
            color = onSurf,
            fontFamily = Mono,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = valueModifier,
        )
        if (interactive && value.isNotBlank()) {
            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (masked) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// --- Driver's License ------------------------------------------------------

@Composable
private fun DriversLicenseHero(
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
    onCopy: (String, String) -> Unit,
    interactive: Boolean,
) {
    val bg = Brush.linearGradient(
        listOf(Color(0xFFFFF3E0), Color(0xFFFFD7B5), Color(0xFFFFC089)),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
    ) {
        // Red header band — classic US-style license accent.
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(Color(0xFFB3261E))
                .align(Alignment.TopCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DRIVER LICENSE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    fields.value("Country/State").uppercase().ifBlank { title.uppercase() },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                )
            }
        }

        Column(Modifier.fillMaxSize().padding(top = 36.dp, start = 16.dp, end = 16.dp, bottom = 10.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                PhotoSlot(attachment, Modifier.fillMaxWidth(0.26f).fillMaxSize())
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    LabelValue("Full name", fields.value("Full name"),
                        Color(0xFF4E2A00).copy(alpha = 0.7f), Color(0xFF2A1500), 14)
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1.2f)) {
                            LabelValue("DOB", fields.value("Date of birth"),
                                Color(0xFF4E2A00).copy(alpha = 0.7f), Color(0xFF2A1500), 12, mono = true)
                        }
                        Box(Modifier.weight(1f)) {
                            LabelValue("Expiry", fields.value("Expiry"),
                                Color(0xFF4E2A00).copy(alpha = 0.7f), Color(0xFF2A1500), 12, mono = true)
                        }
                        Box(Modifier.weight(0.6f)) {
                            LabelValue("Class", fields.value("Class"),
                                Color(0xFF4E2A00).copy(alpha = 0.7f), Color(0xFF2A1500), 12)
                        }
                    }
                }
            }
            val no = fields.value("License number")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable(enabled = interactive && no.isNotBlank()) {
                        onCopy("License number", no)
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LICENSE NO.",
                        color = Color(0xFF4E2A00).copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = no.ifBlank { "•••• •••• ••••" },
                        color = Color(0xFF2A1500),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = Mono,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// --- Birth Certificate -----------------------------------------------------

@Composable
private fun CompactBirthCertificateHero(
    title: String,
    fields: List<Pair<String, String>>,
) {
    val parchment = Color(0xFFFAF6E8)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = parchment,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .border(1.dp, Color(0xFF8B6F31).copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Text(
                "CERTIFICATE OF BIRTH",
                color = Color(0xFF8B6F31),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            LabelValue(
                "Full name",
                fields.value("Full name").ifBlank { title },
                Color(0xFF6B5E3B),
                Color(0xFF1A1404),
                17,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                LabelValue(
                    "Date of birth",
                    fields.value("Date of birth"),
                    Color(0xFF6B5E3B),
                    Color(0xFF1A1404),
                    13,
                    mono = true,
                )
                Spacer(Modifier.width(18.dp))
                LabelValue(
                    "Place of birth",
                    fields.value("Place of birth"),
                    Color(0xFF6B5E3B),
                    Color(0xFF1A1404),
                    13,
                )
            }
            Spacer(Modifier.height(10.dp))
            LabelValue(
                "Registration no.",
                fields.value("Registration number"),
                Color(0xFF6B5E3B),
                Color(0xFF1A1404),
                13,
                mono = true,
            )
        }
    }
}

@Composable
private fun BirthCertificateHero(
    title: String,
    fields: List<Pair<String, String>>,
    attachment: ByteArray?,
) {
    val parchment = Color(0xFFFAF6E8)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(parchment)
            .border(1.dp, Color(0xFF8B6F31).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Ornate-style header.
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "❦   CERTIFICATE   ❦",
                    color = Color(0xFF8B6F31),
                    fontSize = 11.sp,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "of Birth",
                    color = Color(0xFF1A1404),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "issued under official record",
                    color = Color(0xFF8B6F31),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            if (attachment != null) {
                PhotoSlot(attachment, Modifier.width(90.dp).height(110.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelValue("Full name", fields.value("Full name"),
                    Color(0xFF6B5E3B), Color(0xFF1A1404), 16)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabelValue("Date of birth", fields.value("Date of birth"),
                        Color(0xFF6B5E3B), Color(0xFF1A1404), 13, mono = true)
                    LabelValue("Place of birth", fields.value("Place of birth"),
                        Color(0xFF6B5E3B), Color(0xFF1A1404), 13)
                }
                LabelValue("Father's name", fields.value("Father's name"),
                    Color(0xFF6B5E3B), Color(0xFF1A1404), 13)
                LabelValue("Mother's name", fields.value("Mother's name"),
                    Color(0xFF6B5E3B), Color(0xFF1A1404), 13)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabelValue("Reg. no.", fields.value("Registration number"),
                        Color(0xFF6B5E3B), Color(0xFF1A1404), 13, mono = true)
                    LabelValue("Date of issue", fields.value("Date of issue"),
                        Color(0xFF6B5E3B), Color(0xFF1A1404), 13, mono = true)
                }
            }
        }
    }
}

// --- Tax Number ----------------------------------------------------------

@Composable
private fun TaxNumberHero(
    title: String,
    fields: List<Pair<String, String>>,
    onCopy: (String, String) -> Unit,
    interactive: Boolean,
) {
    val taxNo = fields.value("Tax number")
    val name = fields.value("Full name")
    val country = fields.value("Country")

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFF0D8),
        modifier = Modifier
            .fillMaxWidth()
            .mainPageCardFrame(interactive)
            .border(1.dp, Color(0xFFE3A24A).copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title.ifBlank { "TAX IDENTIFICATION" }.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            
            if (country.isNotBlank()) {
                Text(
                    text = country,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = interactive && taxNo.isNotBlank()) {
                        onCopy("Tax number", taxNo)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "TAX NO.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        taxNo.ifBlank { "•••• •••• ••••" },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = Mono,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun QrCodeHero(
    title: String,
    fields: List<Pair<String, String>>,
    onCopy: (String, String) -> Unit,
    onShare: ((String, String) -> Unit)? = null,
    interactive: Boolean,
) {
    val data = fields.value("Data").ifBlank { fields.value("Content") }
    var bmp by remember(data) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(data) {
        if (data.isNotBlank()) {
            val generated = withContext(Dispatchers.Default) {
                QrGenerator.generate(data)
            }
            bmp = generated
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFEAF7EE),
        modifier = Modifier
            .fillMaxWidth()
            .mainPageCardFrame(interactive)
            .border(1.dp, Color(0xFF71B985).copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // QR Code Section
            val currentBmp = bmp
            if (currentBmp != null) {
                Image(
                    bitmap = currentBmp.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = interactive) {
                            onCopy("QR Data", data)
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // Info Section
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title.ifBlank { "QR CODE" }.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (interactive && onShare != null && data.isNotBlank()) {
                        IconButton(onClick = { onShare(data, title) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (data.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = data,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = Mono
                    )
                }
            }
        }
    }
}

// --- Note ------------------------------------------------------------------

@Composable
private fun NoteHero(
    title: String,
    fields: List<Pair<String, String>>,
    onCopy: (String, String) -> Unit,
    interactive: Boolean,
) {
    val content = fields.value("Content")
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .mainPageCardFrame(interactive)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title.ifBlank { "NOTE" }.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            
            Spacer(Modifier.height(12.dp))

            Text(
                text = content.ifBlank { "No content" },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = interactive && content.isNotBlank()) {
                        onCopy("Note content", content)
                    },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
