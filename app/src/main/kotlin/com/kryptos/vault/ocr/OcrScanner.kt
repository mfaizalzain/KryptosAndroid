package com.kryptos.vault.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Single OCR element with its on-image bounds — used by spatial parsers. */
data class OcrToken(val text: String, val bounds: Rect)

/** Result of a structured OCR pass: the flat text ML Kit produces plus per-element tokens. */
data class OcrResult(val text: String, val tokens: List<OcrToken>)

/**
 * On-device OCR — text never leaves the phone. ML Kit's Latin recognizer is bundled
 * with the app, so no Play Services download or network call is required.
 */
object OcrScanner {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun extractText(bitmap: Bitmap, rotationDegrees: Int = 0): String =
        recognize(bitmap, rotationDegrees).text

    suspend fun extractStructured(bitmap: Bitmap, rotationDegrees: Int = 0): OcrResult {
        val result = recognize(bitmap, rotationDegrees)
        val tokens = buildList {
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val rect = element.boundingBox ?: continue
                        add(OcrToken(element.text, rect))
                    }
                }
            }
        }
        return OcrResult(result.text, tokens)
    }

    private suspend fun recognize(bitmap: Bitmap, rotationDegrees: Int): Text =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
