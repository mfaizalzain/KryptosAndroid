package com.kryptos.vault.nfc

import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.LDSFileUtil
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File

/** Result of a successful eMRTD scan. */
data class PassportScan(
    val documentNumber: String,
    val surname: String,
    val givenNames: String,
    val nationality: String,
    val dateOfBirth: String,   // YYMMDD as encoded in MRZ
    val sex: String,           // M / F / X
    val expiry: String,        // YYMMDD
    val photoBytes: ByteArray?,
    val photoMimeType: String?, // image/jpeg or image/jp2
)

/**
 * Reads an ICAO 9303 e-passport over NFC using BAC. The MRZ key inputs must come from the
 * passport's photo page (the user types them in, or OCR fills them):
 *   - [docNumber]: passport number, alphanumeric, e.g. "A12345678"
 *   - [dobYYMMDD]: date of birth, 6 digits
 *   - [expiryYYMMDD]: expiry date, 6 digits
 *
 * All work is blocking I/O — call from a background dispatcher.
 */
object PassportNfcReader {

    fun read(
        isoDep: IsoDep,
        docNumber: String,
        dobYYMMDD: String,
        expiryYYMMDD: String,
    ): PassportScan {
        isoDep.timeout = 10_000
        val cardService = CardService.getInstance(isoDep).also { it.open() }
        val service = PassportService(
            cardService,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            PassportService.DEFAULT_MAX_BLOCKSIZE,
            false,
            false,
        )
        try {
            service.open()
            service.sendSelectApplet(false)
            service.doBAC(BACKey(docNumber.trim().uppercase(), dobYYMMDD, expiryYYMMDD))

            val dg1 = service.getInputStream(PassportService.EF_DG1).use { input ->
                LDSFileUtil.getLDSFile(PassportService.EF_DG1, input) as DG1File
            }
            val mrz = dg1.mrzInfo

            val (photoBytes, mime) = try {
                service.getInputStream(PassportService.EF_DG2).use { input ->
                    val dg2 = LDSFileUtil.getLDSFile(PassportService.EF_DG2, input) as DG2File
                    val face = dg2.faceInfos.firstOrNull()?.faceImageInfos?.firstOrNull()
                    if (face != null) {
                        val len = face.imageLength
                        val bytes = ByteArray(len)
                        val read = face.imageInputStream.use { it.readNBytesCompat(bytes) }
                        val data = if (read == len) bytes else bytes.copyOf(read)
                        data to face.mimeType
                    } else null to null
                }
            } catch (t: Throwable) {
                null to null
            }

            return PassportScan(
                documentNumber = mrz.documentNumber.trimEnd('<').trim(),
                surname = mrz.primaryIdentifier.replace("<", " ").trim(),
                givenNames = mrz.secondaryIdentifier.replace("<", " ").trim(),
                nationality = mrz.nationality.trimEnd('<'),
                dateOfBirth = mrz.dateOfBirth,
                sex = mrz.gender?.toString() ?: "",
                expiry = mrz.dateOfExpiry,
                photoBytes = photoBytes,
                photoMimeType = mime,
            )
        } finally {
            runCatching { service.close() }
            runCatching { cardService.close() }
        }
    }

    private fun java.io.InputStream.readNBytesCompat(buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val r = read(buf, off, buf.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }
}
