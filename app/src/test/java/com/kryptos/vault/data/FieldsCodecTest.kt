package com.kryptos.vault.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldsCodecTest {

    @Test
    fun `encode produces json array with keys and values`() {
        val json = FieldsCodec.encode(listOf("Name" to "Faizal", "Number" to "1234 5678"))
        // key order within a JSONObject is not contractual; verify decode round-trip
        val decoded = FieldsCodec.decode(json)
        assertEquals(listOf("Name" to "Faizal", "Number" to "1234 5678"), decoded)
    }

    @Test
    fun `decode round-trips encode`() {
        val original = listOf(
            "Full name" to "Jane Doe",
            "Card number" to "4111 1111 1111 1111",
            "Expiry" to "12/28",
        )
        assertEquals(original, FieldsCodec.decode(FieldsCodec.encode(original)))
    }

    @Test
    fun `decode of blank returns empty list`() {
        assertEquals(emptyList<Pair<String, String>>(), FieldsCodec.decode(""))
        assertEquals(emptyList<Pair<String, String>>(), FieldsCodec.decode("   "))
    }

    @Test
    fun `decode preserves field order`() {
        val json = """[{"k":"b","v":"2"},{"k":"a","v":"1"},{"k":"c","v":"3"}]"""
        val decoded = FieldsCodec.decode(json)
        assertEquals(listOf("b" to "2", "a" to "1", "c" to "3"), decoded)
    }

    @Test
    fun `encode escapes quotes and special characters`() {
        val json = FieldsCodec.encode(listOf("Note" to "say \"hi\"\nnext line"))
        val decoded = FieldsCodec.decode(json)
        assertEquals("say \"hi\"\nnext line", decoded.first().second)
    }

    @Test
    fun `empty list encodes as empty array`() {
        assertEquals("[]", FieldsCodec.encode(emptyList()))
        assertEquals(emptyList<Pair<String, String>>(), FieldsCodec.decode("[]"))
    }
}
