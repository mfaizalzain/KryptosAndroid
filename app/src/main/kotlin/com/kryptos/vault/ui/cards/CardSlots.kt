package com.kryptos.vault.ui.cards
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PermIdentity
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PhotoSlot(bytes: ByteArray?, modifier: Modifier) {
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
internal fun LabelValue(
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


internal fun maskCardNumber(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    val last4 = digits.takeLast(4).padStart(4, '•')
    return "•••• •••• •••• $last4"
}
