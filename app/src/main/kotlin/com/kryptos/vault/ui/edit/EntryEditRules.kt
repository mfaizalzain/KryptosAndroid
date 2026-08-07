package com.kryptos.vault.ui.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.kryptos.vault.data.Template
import com.kryptos.vault.ui.scan.QrPayloadType
import com.kryptos.vault.ui.scan.QrPayloads

// --- Template presentation helpers -----------------------------------------

internal fun templateIcon(t: Template): ImageVector = when (t) {
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

internal fun prettyTemplate(t: Template): String = when (t) {
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

internal fun defaultFieldsFor(template: Template): List<String> = when (template) {
    Template.ID_CARD -> listOf("Full name", "ID number", "Date of birth", "Nationality", "Expiry")
    Template.PASSPORT -> listOf("Surname", "Given names", "Passport number", "Nationality", "Date of birth", "Sex", "Expiry")
    Template.DRIVERS_LICENSE -> listOf("Full name", "License number", "Class", "Date of birth", "Expiry", "Country/State")
    Template.BIRTH_CERTIFICATE -> listOf("Full name", "Date of birth", "Place of birth", "Father's name", "Mother's name", "Registration number", "Date of issue")
    Template.PAYMENT_CARD -> listOf("Issuer", "Cardholder", "Number", "Expiry", "CVV")
    Template.BANK_ACCOUNT -> listOf("Bank", "Account holder", "Account number", "IBAN", "SWIFT/BIC", "PIN")
    Template.TAX_NUMBER -> listOf("Full name", "Tax number", "Country")
    Template.API_KEY -> listOf("Service", "Environment", "Key", "Secret")
    Template.NOTE -> listOf("Content")
    Template.QR_CODE -> QrPayloads.defaultFields(QrPayloadType.TEXT).map { it.first }
}

// --- Scan capability rules --------------------------------------------------

internal fun supportsCameraScan(t: Template): Boolean = when (t) {
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

internal fun supportsNfcScan(t: Template): Boolean = t == Template.PASSPORT || t == Template.PAYMENT_CARD

internal fun supportsImportPanel(t: Template): Boolean =
    supportsCameraScan(t) || supportsNfcScan(t) || t == Template.API_KEY || t == Template.NOTE || t == Template.QR_CODE

// --- Field input rules ------------------------------------------------------

internal fun isDateField(name: String, template: Template): Boolean {
    val n = name.lowercase()
    if (template == Template.PAYMENT_CARD && (n.contains("expiry") || n.contains("expires"))) return false
    return n.contains("date") || n.contains("expiry") || n.contains("expires") || n.contains("dob")
}

internal fun isNumericField(name: String, template: Template): Boolean {
    val n = name.lowercase()
    if (template == Template.PAYMENT_CARD && (n.contains("expiry") || n.contains("expires"))) return true
    return n == "number" || n.contains("cvv") || n.contains("pin") || n.contains("cvc") || n == "account number"
}

/** Formats a payment-card expiry as MM/YY while typing. */
internal class ExpiryVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
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

internal fun fieldsForSave(template: Template, fields: List<Pair<String, String>>): List<Pair<String, String>> =
    if (template == Template.QR_CODE) QrPayloads.withGeneratedData(fields) else fields

// --- State savers -----------------------------------------------------------

internal val TemplateSaver: Saver<Template, String> = Saver(
    save = { it.name },
    restore = { runCatching { Template.valueOf(it) }.getOrDefault(Template.ID_CARD) },
)

internal val FieldsListSaver: Saver<SnapshotStateList<Pair<String, String>>, Any> =
    listSaver(
        save = { list -> list.flatMap { listOf(it.first, it.second) } },
        restore = { flat ->
            val list = mutableStateListOf<Pair<String, String>>()
            var i = 0
            val flatList = flat as List<*>
            while (i + 1 < flatList.size) {
                val first = flatList[i] as? String ?: ""
                val second = flatList[i + 1] as? String ?: ""
                list.add(first to second)
                i += 2
            }
            list
        },
    )

// --- QR payload type presentation -------------------------------------------

internal fun QrPayloadType.defaultQrTitle(): String = when (this) {
    QrPayloadType.TEXT -> "QR code"
    QrPayloadType.CONTACT -> "Contact QR"
    QrPayloadType.WIFI -> "Wi-Fi QR"
    QrPayloadType.EMAIL -> "Email QR"
    QrPayloadType.PHONE -> "Phone QR"
    QrPayloadType.SMS -> "SMS QR"
    QrPayloadType.LOCATION -> "Location QR"
    QrPayloadType.CALENDAR -> "Calendar QR"
    QrPayloadType.PAYMENT -> "Payment QR"
}

internal fun QrPayloadType.icon(): ImageVector = when (this) {
    QrPayloadType.TEXT -> Icons.Default.TextFields
    QrPayloadType.CONTACT -> Icons.Default.ContactPhone
    QrPayloadType.WIFI -> Icons.Default.Wifi
    QrPayloadType.EMAIL -> Icons.Default.Email
    QrPayloadType.PHONE -> Icons.Default.Phone
    QrPayloadType.SMS -> Icons.Default.Sms
    QrPayloadType.LOCATION -> Icons.Default.Place
    QrPayloadType.CALENDAR -> Icons.Default.Event
    QrPayloadType.PAYMENT -> Icons.Default.Payments
}
