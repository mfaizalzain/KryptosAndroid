package com.kryptos.vault.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadsTest {

    @Test
    fun `text payload is raw data`() {
        val payload = QrPayloads.build(QrPayloadType.TEXT, listOf("Data" to "hello world"))
        assertEquals("hello world", payload)
    }

    @Test
    fun `phone payload uses tel scheme`() {
        val payload = QrPayloads.build(QrPayloadType.PHONE, listOf("Phone" to "+60123456789"))
        assertEquals("tel:+60123456789", payload)
    }

    @Test
    fun `vcard includes all populated fields in order`() {
        val payload = QrPayloads.build(
            QrPayloadType.CONTACT,
            listOf(
                "Full name" to "Faizal Zain",
                "Phone" to "+60123456789",
                "Email" to "faizal@example.com",
                "Organization" to "Acme",
                "Job title" to "Engineer",
                "Website" to "https://example.com",
                "Address" to "1 Jalan Bukit",
                "Note" to "colleague",
            )
        )
        val lines = payload.split("\n")
        assertTrue(lines.first() == "BEGIN:VCARD")
        assertTrue(lines.contains("VERSION:3.0"))
        assertTrue(lines.contains("FN:Faizal Zain"))
        assertTrue(lines.contains("TEL:+60123456789"))
        assertTrue(lines.contains("EMAIL:faizal@example.com"))
        assertTrue(lines.contains("ORG:Acme"))
        assertTrue(lines.contains("TITLE:Engineer"))
        assertTrue(lines.contains("URL:https://example.com"))
        assertTrue(lines.contains("ADR:;;1 Jalan Bukit;;;;"))
        assertTrue(lines.contains("NOTE:colleague"))
        assertTrue(lines.last() == "END:VCARD")
    }

    @Test
    fun `vcard skips blank fields`() {
        val payload = QrPayloads.build(
            QrPayloadType.CONTACT,
            listOf("Full name" to "Only Name", "Email" to "")
        )
        assertTrue(payload.contains("FN:Only Name"))
        assertTrue(!payload.contains("EMAIL:"))
        assertTrue(!payload.contains("TEL:"))
    }

    @Test
    fun `vcard escapes semicolons and newlines`() {
        val payload = QrPayloads.build(
            QrPayloadType.CONTACT,
            listOf("Full name" to "Doe, John", "Note" to "line1\nline2")
        )
        assertTrue(payload.contains("FN:Doe\\, John"))
        assertTrue(payload.contains("NOTE:line1\\nline2"))
    }

    @Test
    fun `wifi payload with WPA2 and password`() {
        val payload = QrPayloads.build(
            QrPayloadType.WIFI,
            listOf("Network name" to "HomeNet", "Security" to "WPA2", "Password" to "secret;123")
        )
        assertEquals("WIFI:T:WPA2;S:HomeNet;P:secret\\;123;;", payload)
    }

    @Test
    fun `wifi payload without password uses nopass`() {
        val payload = QrPayloads.build(
            QrPayloadType.WIFI,
            listOf("Network name" to "OpenNet", "Security" to "")
        )
        assertEquals("WIFI:T:nopass;S:OpenNet;;", payload)
    }

    @Test
    fun `wifi payload marks hidden network`() {
        val payload = QrPayloads.build(
            QrPayloadType.WIFI,
            listOf("Network name" to "Hidden", "Security" to "WPA", "Password" to "pw", "Hidden" to "true")
        )
        assertTrue(payload.contains("H:true;"))
    }

    @Test
    fun `email payload with subject and body`() {
        val payload = QrPayloads.build(
            QrPayloadType.EMAIL,
            listOf("Email" to "a@b.com", "Subject" to "Hi there", "Body" to "See you")
        )
        assertEquals("mailto:a@b.com?subject=Hi%20there&body=See%20you", payload)
    }

    @Test
    fun `sms payload with body`() {
        val payload = QrPayloads.build(
            QrPayloadType.SMS,
            listOf("Phone" to "12345", "Message" to "Hello")
        )
        assertEquals("sms:12345?body=Hello", payload)
    }

    @Test
    fun `geo payload without label`() {
        val payload = QrPayloads.build(
            QrPayloadType.LOCATION,
            listOf("Latitude" to "3.1390", "Longitude" to "101.6869")
        )
        assertEquals("geo:3.1390,101.6869", payload)
    }

    @Test
    fun `geo payload with label uses query form`() {
        val payload = QrPayloads.build(
            QrPayloadType.LOCATION,
            listOf("Latitude" to "3.1390", "Longitude" to "101.6869", "Label" to "KLCC")
        )
        assertEquals("geo:3.1390,101.6869?q=3.1390,101.6869(KLCC)", payload)
    }

    @Test
    fun `calendar payload renders vcalendar with normalized dates`() {
        val payload = QrPayloads.build(
            QrPayloadType.CALENDAR,
            listOf(
                "Title" to "Meeting",
                "Starts" to "2026-08-10 09:00",
                "Ends" to "2026-08-10 10:00",
                "Location" to "Room 4",
            )
        )
        val lines = payload.split("\n")
        assertTrue(lines.first() == "BEGIN:VCALENDAR")
        assertTrue(lines.contains("VERSION:2.0"))
        assertTrue(lines.contains("BEGIN:VEVENT"))
        assertTrue(lines.contains("SUMMARY:Meeting"))
        assertTrue(lines.contains("DTSTART:20260810T0900"))
        assertTrue(lines.contains("DTEND:20260810T1000"))
        assertTrue(lines.contains("LOCATION:Room 4"))
        assertTrue(lines.contains("END:VEVENT"))
        assertTrue(lines.last() == "END:VCALENDAR")
    }

    @Test
    fun `payment payload passes through uri when it has scheme`() {
        val payload = QrPayloads.build(
            QrPayloadType.PAYMENT,
            listOf("URI or address" to "bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
        )
        assertEquals("bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", payload)
    }

    @Test
    fun `payment payload appends params to bare address`() {
        // no ":" in the address -> params are appended
        val payload = QrPayloads.build(
            QrPayloadType.PAYMENT,
            listOf("URI or address" to "1234567890", "Amount" to "100", "Note" to "rent")
        )
        assertEquals("1234567890?amount=100&message=rent", payload)
    }

    @Test
    fun `payment payload with scheme passes through untouched`() {
        // address containing ":" is treated as a full URI -> returned as-is
        val payload = QrPayloads.build(
            QrPayloadType.PAYMENT,
            listOf("URI or address" to "iban:DE89370400440532013000", "Amount" to "100")
        )
        assertEquals("iban:DE89370400440532013000", payload)
    }

    @Test
    fun `withGeneratedData normalizes fields and stores payload under Data`() {
        val result = QrPayloads.withGeneratedData(
            listOf(
                "QR type" to "Wi-Fi",
                "Network name" to "Net",
                "Security" to "WPA",
                "Password" to "pw",
            )
        )
        val data = result.firstOrNull { it.first.equals("Data", ignoreCase = true) }?.second
        assertEquals("WIFI:T:WPA;S:Net;P:pw;;", data)
        // QR type field present with label
        assertTrue(result.any { it.first.equals("QR type", ignoreCase = true) && it.second == "Wi-Fi" })
    }
}
