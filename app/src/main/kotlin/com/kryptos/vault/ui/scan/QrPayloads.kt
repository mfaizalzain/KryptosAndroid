package com.kryptos.vault.ui.scan

import java.net.URLEncoder

enum class QrPayloadType(val label: String) {
    TEXT("Text"),
    CONTACT("Contact"),
    WIFI("Wi-Fi"),
    EMAIL("Email"),
    PHONE("Phone"),
    SMS("SMS"),
    LOCATION("Location"),
    CALENDAR("Calendar"),
    PAYMENT("Payment"),
}

object QrPayloads {
    const val TYPE_FIELD = "QR type"
    const val DATA_FIELD = "Data"

    fun defaultFields(type: QrPayloadType): List<Pair<String, String>> = when (type) {
        QrPayloadType.TEXT -> listOf(TYPE_FIELD to type.label, DATA_FIELD to "")
        QrPayloadType.CONTACT -> listOf(
            TYPE_FIELD to type.label,
            DATA_FIELD to "",
            "Full name" to "",
            "Phone" to "",
            "Email" to "",
            "Organization" to "",
            "Job title" to "",
            "Website" to "",
            "Address" to "",
            "Note" to "",
        )
        QrPayloadType.WIFI -> listOf(
            TYPE_FIELD to type.label,
            DATA_FIELD to "",
            "Network name" to "",
            "Password" to "",
            "Security" to "WPA",
            "Hidden" to "false",
        )
        QrPayloadType.EMAIL -> listOf(
            TYPE_FIELD to type.label,
            DATA_FIELD to "",
            "Email" to "",
            "Subject" to "",
            "Body" to "",
        )
        QrPayloadType.PHONE -> listOf(TYPE_FIELD to type.label, DATA_FIELD to "", "Phone" to "")
        QrPayloadType.SMS -> listOf(TYPE_FIELD to type.label, DATA_FIELD to "", "Phone" to "", "Message" to "")
        QrPayloadType.LOCATION -> listOf(
            TYPE_FIELD to type.label,
            DATA_FIELD to "",
            "Latitude" to "",
            "Longitude" to "",
            "Label" to "",
        )
        QrPayloadType.CALENDAR -> listOf(
            TYPE_FIELD to type.label,
            DATA_FIELD to "",
            "Title" to "",
            "Starts" to "",
            "Ends" to "",
            "Location" to "",
            "Description" to "",
        )
        QrPayloadType.PAYMENT -> listOf(
            TYPE_FIELD to type.label,
            DATA_FIELD to "",
            "URI or address" to "",
            "Amount" to "",
            "Network" to "",
            "Note" to "",
        )
    }

    fun selectedType(fields: List<Pair<String, String>>): QrPayloadType =
        fields.valueOf(TYPE_FIELD)
            ?.let { raw -> QrPayloadType.entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) } }
            ?: QrPayloadType.TEXT

    fun withGeneratedData(fields: List<Pair<String, String>>): List<Pair<String, String>> {
        val type = selectedType(fields)
        val payload = build(type, fields)
        val normalized = if (fields.any { it.first.equals(TYPE_FIELD, ignoreCase = true) }) {
            fields
        } else {
            listOf(TYPE_FIELD to type.label) + fields
        }
        return normalized.upsert(DATA_FIELD, payload)
    }

    fun build(type: QrPayloadType, fields: List<Pair<String, String>>): String {
        val data = fields.valueOf(DATA_FIELD).orEmpty()
        return when (type) {
            QrPayloadType.TEXT -> data
            QrPayloadType.CONTACT -> buildVCard(fields)
            QrPayloadType.WIFI -> buildWifi(fields)
            QrPayloadType.EMAIL -> buildEmail(fields)
            QrPayloadType.PHONE -> "tel:${fields.valueOf("Phone").orEmpty()}"
            QrPayloadType.SMS -> buildSms(fields)
            QrPayloadType.LOCATION -> buildLocation(fields)
            QrPayloadType.CALENDAR -> buildCalendar(fields)
            QrPayloadType.PAYMENT -> buildPayment(fields)
        }
    }

    private fun buildVCard(fields: List<Pair<String, String>>): String {
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
        fields.valueOf("Full name")?.takeIf { it.isNotBlank() }?.let {
            lines += "FN:${escapeVCard(it)}"
        }
        fields.valueOf("Phone")?.takeIf { it.isNotBlank() }?.let {
            lines += "TEL:${escapeVCard(it)}"
        }
        fields.valueOf("Email")?.takeIf { it.isNotBlank() }?.let {
            lines += "EMAIL:${escapeVCard(it)}"
        }
        fields.valueOf("Organization")?.takeIf { it.isNotBlank() }?.let {
            lines += "ORG:${escapeVCard(it)}"
        }
        fields.valueOf("Job title")?.takeIf { it.isNotBlank() }?.let {
            lines += "TITLE:${escapeVCard(it)}"
        }
        fields.valueOf("Website")?.takeIf { it.isNotBlank() }?.let {
            lines += "URL:${escapeVCard(it)}"
        }
        fields.valueOf("Address")?.takeIf { it.isNotBlank() }?.let {
            lines += "ADR:;;${escapeVCard(it)};;;;"
        }
        fields.valueOf("Note")?.takeIf { it.isNotBlank() }?.let {
            lines += "NOTE:${escapeVCard(it)}"
        }
        lines += "END:VCARD"
        return lines.joinToString("\n")
    }

    private fun buildWifi(fields: List<Pair<String, String>>): String {
        val security = fields.valueOf("Security")?.ifBlank { "nopass" }.orEmpty()
        val ssid = fields.valueOf("Network name").orEmpty()
        val password = fields.valueOf("Password").orEmpty()
        val hidden = fields.valueOf("Hidden")?.equals("true", ignoreCase = true) == true
        return buildString {
            append("WIFI:")
            append("T:${escapeWifi(security)};")
            append("S:${escapeWifi(ssid)};")
            if (password.isNotBlank()) append("P:${escapeWifi(password)};")
            if (hidden) append("H:true;")
            append(";")
        }
    }

    private fun buildEmail(fields: List<Pair<String, String>>): String {
        val email = fields.valueOf("Email").orEmpty()
        val params = listOfNotNull(
            fields.valueOf("Subject")?.takeIf { it.isNotBlank() }?.let { "subject=${encode(it)}" },
            fields.valueOf("Body")?.takeIf { it.isNotBlank() }?.let { "body=${encode(it)}" },
        )
        return "mailto:$email" + params.takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?").orEmpty()
    }

    private fun buildSms(fields: List<Pair<String, String>>): String {
        val phone = fields.valueOf("Phone").orEmpty()
        val body = fields.valueOf("Message").orEmpty()
        return "sms:$phone" + body.takeIf { it.isNotBlank() }?.let { "?body=${encode(it)}" }.orEmpty()
    }

    private fun buildLocation(fields: List<Pair<String, String>>): String {
        val lat = fields.valueOf("Latitude").orEmpty()
        val lng = fields.valueOf("Longitude").orEmpty()
        val label = fields.valueOf("Label").orEmpty()
        return if (label.isBlank()) {
            "geo:$lat,$lng"
        } else {
            "geo:$lat,$lng?q=$lat,$lng(${encode(label)})"
        }
    }

    private fun buildCalendar(fields: List<Pair<String, String>>): String {
        val lines = mutableListOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT")
        fields.valueOf("Title")?.takeIf { it.isNotBlank() }?.let { lines += "SUMMARY:${escapeVCard(it)}" }
        fields.valueOf("Starts")?.takeIf { it.isNotBlank() }?.let { lines += "DTSTART:${calendarDate(it)}" }
        fields.valueOf("Ends")?.takeIf { it.isNotBlank() }?.let { lines += "DTEND:${calendarDate(it)}" }
        fields.valueOf("Location")?.takeIf { it.isNotBlank() }?.let { lines += "LOCATION:${escapeVCard(it)}" }
        fields.valueOf("Description")?.takeIf { it.isNotBlank() }?.let { lines += "DESCRIPTION:${escapeVCard(it)}" }
        lines += "END:VEVENT"
        lines += "END:VCALENDAR"
        return lines.joinToString("\n")
    }

    private fun buildPayment(fields: List<Pair<String, String>>): String {
        val base = fields.valueOf("URI or address").orEmpty()
        val amount = fields.valueOf("Amount").orEmpty()
        val network = fields.valueOf("Network").orEmpty()
        val note = fields.valueOf("Note").orEmpty()
        if (base.contains(":") || amount.isBlank() && network.isBlank() && note.isBlank()) return base
        val params = listOfNotNull(
            amount.takeIf { it.isNotBlank() }?.let { "amount=${encode(it)}" },
            network.takeIf { it.isNotBlank() }?.let { "network=${encode(it)}" },
            note.takeIf { it.isNotBlank() }?.let { "message=${encode(it)}" },
        )
        return base + params.takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?").orEmpty()
    }

    private fun calendarDate(value: String): String =
        value.trim().replace("-", "").replace(":", "").replace(" ", "T")

    private fun escapeVCard(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")

    private fun escapeWifi(value: String): String =
        value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun List<Pair<String, String>>.valueOf(name: String): String? =
        firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    private fun List<Pair<String, String>>.upsert(name: String, value: String): List<Pair<String, String>> {
        var replaced = false
        val updated = map { field ->
            if (field.first.equals(name, ignoreCase = true)) {
                replaced = true
                field.first to value
            } else {
                field
            }
        }
        return if (replaced) updated else listOf(name to value) + updated
    }
}
