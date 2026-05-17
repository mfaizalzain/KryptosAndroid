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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // In non-interactive mode (used by the list as thumbnails) we drop all internal
    // click handlers so the parent's tap-to-open isn't swallowed by reveal/copy controls.
    val effectiveCopy: (String, String) -> Unit = if (interactive) onCopy else { _, _ -> }
    when (template) {
        Template.ID_CARD -> IdCardHero(title, fields, attachment, effectiveCopy, interactive)
        Template.PASSPORT -> if (interactive) {
            PassportHero(title, fields, attachment, effectiveCopy, interactive)
        } else {
            CompactPassportHero(title, fields)
        }
        Template.DRIVERS_LICENSE -> DriversLicenseHero(title, fields, attachment, effectiveCopy, interactive)
        Template.BIRTH_CERTIFICATE -> if (interactive) {
            BirthCertificateHero(title, fields, attachment)
        } else {
            CompactBirthCertificateHero(title, fields)
        }
        Template.PAYMENT_CARD -> PaymentCardHero(title, fields, effectiveCopy, interactive)
        Template.BANK_ACCOUNT -> BankAccountHero(title, fields, effectiveCopy, interactive)
        Template.TAX_NUMBER -> TaxNumberHero(title, fields, effectiveCopy, interactive)
        Template.API_KEY -> ApiKeyHero(title, fields, effectiveCopy, interactive)
        Template.NOTE -> NoteHero(title, fields, effectiveCopy, interactive)
        Template.QR_CODE -> QrCodeHero(title, fields, effectiveCopy, onShare, interactive)
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

private fun Modifier.mainPageCardFrame(interactive: Boolean): Modifier =
    if (interactive) this else this.aspectRatio(1.586f)

@Composable
private fun PhotoSlot(bytes: ByteArray?, modifier: Modifier) {
    val bmp = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
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
    val bmp = remember(data) { if (data.isNotBlank()) QrGenerator.generate(data) else null }

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
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
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
