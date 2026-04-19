package it.denv.ocr.server.responses

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OcrResultProcessorTest {

    @Test
    fun ocrResultSerializationContainsTextBlockText() {
        val textBlock = TextBlock(
            text = "Hello World",
            lines = listOf(Line(text = "Hello World", angle = 0f, confidence = 0.9f, recognizedLanguage = "en")),
            boundingBox = Rect(top = 0, bottom = 10, left = 0, right = 100),
            lang = "en"
        )
        val barcode = BarcodeResult(
            boundingBox = null,
            displayValue = "QR-DATA",
            rawValue = "QR-DATA"
        )
        val result = OcrResult(textBlocks = listOf(textBlock), barcodes = listOf(barcode))
        val json = Json.encodeToString(result)
        assertTrue(json.contains("Hello World"))
        assertTrue(json.contains("QR-DATA"))
    }

    @Test
    fun ocrResultEmptyLists() {
        val result = OcrResult(textBlocks = emptyList(), barcodes = emptyList())
        val json = Json.encodeToString(result)
        val decoded = Json.decodeFromString<OcrResult>(json)
        assertTrue(decoded.textBlocks.isEmpty())
        assertTrue(decoded.barcodes.isEmpty())
    }

    @Test
    fun ocrResultMultipleTextBlocks() {
        val blocks = (1..5).map { i ->
            TextBlock(
                text = "Block $i",
                lines = listOf(Line(text = "Line $i", angle = 0f, confidence = 0.8f, recognizedLanguage = "en")),
                boundingBox = Rect(top = i * 10, bottom = i * 10 + 10, left = 0, right = 50),
                lang = "en"
            )
        }
        val result = OcrResult(textBlocks = blocks, barcodes = emptyList())
        val json = Json.encodeToString(result)
        val decoded = Json.decodeFromString<OcrResult>(json)
        assertEquals(5, decoded.textBlocks.size)
        assertEquals("Block 3", decoded.textBlocks[2].text)
    }
}
