package it.denv.ocr.server.responses

import org.junit.Assert.*
import org.junit.Test

class RectConversionTest {

    @Test
    fun convertToRectWithValidInput() {
        val androidRect = android.graphics.Rect(10, 20, 30, 40)
        val result = OcrResultProcessor.convertToRect(androidRect)
        assertEquals(Rect(top = 20, bottom = 40, left = 10, right = 30), result)
    }

    @Test
    fun convertToRectWithNullReturnsNull() {
        val result = OcrResultProcessor.convertToRect(null)
        assertNull(result)
    }

    @Test
    fun convertToRectZeroRect() {
        val androidRect = android.graphics.Rect(0, 0, 0, 0)
        val result = OcrResultProcessor.convertToRect(androidRect)
        assertEquals(Rect(top = 0, bottom = 0, left = 0, right = 0), result)
    }
}
