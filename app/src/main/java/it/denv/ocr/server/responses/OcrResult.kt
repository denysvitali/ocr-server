package it.denv.ocr.server.responses

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.text.Text
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class OcrResult(val textBlocks: @Contextual List<TextBlock>, val barcodes: List<BarcodeResult>)

@Serializable
data class BarcodeResult(val boundingBox: Rect?, val displayValue: String?, val rawValue: String?)

class OcrResultProcessor {
    companion object {
        const val TAG = "OcrResultProcessor"
        fun process(barcodes: MutableList<Barcode>, ocrResult: Text): OcrResult {
            val textBlocks = mutableListOf<TextBlock>()
            for (tb in ocrResult.textBlocks) {
                val bb = tb.boundingBox
                textBlocks.add(
                    TextBlock(
                        text = tb.text,
                        lang = tb.recognizedLanguage,
                        boundingBox = convertToRect(tb.boundingBox),
                        lines = tb.lines.map { mapLines(it) }
                    )
                )
            }
            return OcrResult(textBlocks = textBlocks, barcodes = barcodes.map { mapBarcodes(it) })
        }

        private fun mapBarcodes(it: Barcode): BarcodeResult {
            return BarcodeResult(
                boundingBox = convertToRect(it.boundingBox),
                displayValue = it.displayValue,
                rawValue = it.rawValue
            )
        }

        private fun convertToRect(boundingBox: android.graphics.Rect?): Rect? {
            if (boundingBox == null) {
                return null
            }

            return Rect(
                top = boundingBox.top,
                bottom = boundingBox.bottom,
                left = boundingBox.left,
                right = boundingBox.right
            )
        }

        private fun mapLines(line: Text.Line): Line {
            return Line(
                text = line.text,
                angle = line.angle,
                confidence = line.confidence,
                recognizedLanguage = line.recognizedLanguage
            )
        }
    }

}

@Serializable
data class TextBlock(
    val text: String,
    val lines: List<Line>,
    val boundingBox: Rect?,
    val lang: String
)

@Serializable
data class Line(
    val text: String,
    val angle: Float,
    val confidence: Float,
    val recognizedLanguage: String
)

@Serializable
data class Rect(val top: Int, val bottom: Int, val left: Int, val right: Int)