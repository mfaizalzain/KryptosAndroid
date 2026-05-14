package com.kryptos.vault.nfc

import android.nfc.tech.IsoDep
import java.nio.ByteBuffer

/** Result of a successful EMV card scan via NFC. */
data class CardScan(
    val pan: String,
    val expiry: String?, // MM/YY
    val type: String?,   // VISA, MASTERCARD, etc.
)

/**
 * A basic EMV reader that uses ISO-7816 APDUs to extract the PAN and Expiry from
 * contactless credit/debit cards.
 */
object CardNfcReader {

    private val PPSE = byteArrayOf(
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31
    )

    fun read(isoDep: IsoDep): CardScan {
        isoDep.connect()
        isoDep.timeout = 5000

        // 1. Select PPSE to find supported AIDs
        val ppseRes = transceive(isoDep, selectApdu(PPSE))
        if (!isSuccess(ppseRes)) throw Exception("Failed to select PPSE")

        // Look for common AIDs (Visa, Mastercard)
        val aids = listOf(
            byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10), // Visa
            byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10), // Mastercard
            byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x20, 0x10), // Visa Electron
            byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x30, 0x60), // Maestro
        )

        for (aid in aids) {
            val res = transceive(isoDep, selectApdu(aid))
            if (isSuccess(res)) {
                // 2. Read records to find PAN and Expiry (usually in SFI 1 or 2)
                for (sfi in 1..10) {
                    for (rec in 1..10) {
                        val readRes = transceive(isoDep, readRecordApdu(rec, sfi))
                        if (isSuccess(readRes)) {
                            val panMatch = findPan(readRes)
                            if (panMatch != null) {
                                return CardScan(
                                    pan = panMatch.first,
                                    expiry = panMatch.second,
                                    type = detectType(aid)
                                )
                            }
                        }
                    }
                }
            }
        }
        throw Exception("Could not read card data. Make sure it's a contactless EMV card.")
    }

    private fun selectApdu(aid: ByteArray): ByteArray {
        val apdu = ByteBuffer.allocate(aid.size + 6)
        apdu.put(0x00) // CLA
        apdu.put(0xA4.toByte()) // INS (SELECT)
        apdu.put(0x04) // P1
        apdu.put(0x00) // P2
        apdu.put(aid.size.toByte()) // Lc
        apdu.put(aid)
        apdu.put(0x00) // Le
        return apdu.array()
    }

    private fun readRecordApdu(rec: Int, sfi: Int): ByteArray {
        return byteArrayOf(
            0x00, // CLA
            0xB2.toByte(), // INS (READ RECORD)
            rec.toByte(), // P1 (Record number)
            ((sfi shl 3) or 4).toByte(), // P2 (SFI and flags)
            0x00 // Le
        )
    }

    private fun transceive(isoDep: IsoDep, apdu: ByteArray): ByteArray {
        return isoDep.transceive(apdu)
    }

    private fun isSuccess(res: ByteArray): Boolean {
        if (res.size < 2) return false
        val sw1 = res[res.size - 2].toInt() and 0xFF
        val sw2 = res[res.size - 1].toInt() and 0xFF
        return sw1 == 0x90 && sw2 == 0x00
    }

    /** Simple regex-based PAN/Expiry hunter in raw TLV data. */
    private fun findPan(data: ByteArray): Pair<String, String?>? {
        val hex = data.joinToString("") { "%02X".format(it) }
        // PAN is usually tagged with 5A or inside Track 2 Equivalent Data (57)
        // Tag 57: Track 2 Equivalent Data (Format: PAN D Expiry ServiceCode DiscretionaryData)
        val track2Idx = hex.indexOf("57")
        if (track2Idx >= 0) {
            val len = hex.substring(track2Idx + 2, track2Idx + 4).toInt(16)
            val content = hex.substring(track2Idx + 4, track2Idx + 4 + (len * 2))
            val dIdx = content.indexOf("D")
            if (dIdx > 0) {
                val pan = content.substring(0, dIdx)
                val expiry = content.substring(dIdx + 1, dIdx + 5) // YYMM
                val formattedExpiry = "${expiry.substring(2, 4)}/${expiry.substring(0, 2)}"
                return pan to formattedExpiry
            }
        }
        
        // Fallback: search for tag 5A (PAN)
        val panIdx = hex.indexOf("5A")
        if (panIdx >= 0) {
            val len = hex.substring(panIdx + 2, panIdx + 4).toInt(16)
            val pan = hex.substring(panIdx + 4, panIdx + 4 + (len * 2)).trimEnd('F')
            return pan to null
        }
        
        return null
    }

    private fun detectType(aid: ByteArray): String? {
        val hex = aid.joinToString("") { "%02X".format(it) }
        return when {
            hex.startsWith("A000000003") -> "VISA"
            hex.startsWith("A000000004") -> "MASTERCARD"
            else -> "CARD"
        }
    }
}
