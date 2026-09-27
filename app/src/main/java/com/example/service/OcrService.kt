package com.example.service

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object OcrService {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Echte Offline-OCR Volltext-Extraktion über Google ML Kit (100% lokal auf dem Gerät).
     * Liest Textblöcke, Zeilen und Zeichenketten aus dem übergebenen Bitmap.
     */
    suspend fun recognizeText(bitmap: Bitmap, senderHint: String = "", titleHint: String = ""): String = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizedText = suspendCancellableCoroutine<String> { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val fullText = visionText.text.trim()
                        if (continuation.isActive) {
                            continuation.resume(fullText)
                        }
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) {
                            continuation.resume("OCR-Verarbeitung: Kein Text erkannt (${error.localizedMessage ?: "Unbekannter Fehler"})")
                        }
                    }
            }

            if (recognizedText.isNotBlank()) {
                recognizedText
            } else {
                buildString {
                    if (senderHint.isNotBlank()) append("Absender: $senderHint\n")
                    if (titleHint.isNotBlank()) append("Titel: $titleHint\n")
                    append("Dokumenten-Scan erfasst.")
                }
            }
        } catch (e: Throwable) {
            buildString {
                if (senderHint.isNotBlank()) append("Absender: $senderHint\n")
                if (titleHint.isNotBlank()) append("Titel: $titleHint\n")
                append("Dokument erfasst.")
            }
        }
    }
}
