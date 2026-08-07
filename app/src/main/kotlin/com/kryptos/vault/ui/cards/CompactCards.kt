package com.kryptos.vault.ui.cards
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptos.vault.data.Template
import com.kryptos.vault.ui.scan.QrGenerator

@Composable
internal fun CompactIdentityCard(
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
internal fun CompactDocumentCard(
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
internal fun CompactPaymentCard(title: String, fields: List<Pair<String, String>>, attachment: ByteArray?) {
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
internal fun CompactSecretCard(
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
internal fun CompactNoteCard(title: String, fields: List<Pair<String, String>>, attachment: ByteArray?) {
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
internal fun CompactQrCard(title: String, fields: List<Pair<String, String>>) {
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

