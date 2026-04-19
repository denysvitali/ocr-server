package it.denv.ocr.server.responses

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SerializationTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun batteryStatusRoundTrip() {
        val original = BatteryStatus(BatteryState.CHARGING, 85)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BatteryStatus>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun batteryStatusAllStates() {
        for (state in BatteryState.entries) {
            val original = BatteryStatus(state, 50)
            val encoded = json.encodeToString(original)
            val decoded = json.decodeFromString<BatteryStatus>(encoded)
            assertEquals(original, decoded)
        }
    }

    @Test
    fun ocrResultEmptyRoundTrip() {
        val original = OcrResult(textBlocks = emptyList(), barcodes = emptyList())
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<OcrResult>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun ocrResultPopulatedRoundTrip() {
        val textBlock = TextBlock(
            text = "Hello",
            lines = listOf(Line(text = "Hello", angle = 0f, confidence = 0.95f, recognizedLanguage = "en")),
            boundingBox = Rect(top = 0, bottom = 10, left = 0, right = 50),
            lang = "en"
        )
        val barcode = BarcodeResult(
            boundingBox = Rect(top = 0, bottom = 100, left = 0, right = 100),
            displayValue = "test-value",
            rawValue = "test-raw"
        )
        val original = OcrResult(textBlocks = listOf(textBlock), barcodes = listOf(barcode))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<OcrResult>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun rectRoundTrip() {
        val original = Rect(top = 10, bottom = 20, left = 5, right = 30)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<Rect>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun barcodeResultWithNulls() {
        val original = BarcodeResult(boundingBox = null, displayValue = null, rawValue = null)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BarcodeResult>(encoded)
        assertEquals(original, decoded)
    }
}
