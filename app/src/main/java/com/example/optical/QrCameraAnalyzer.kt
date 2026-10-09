package com.example.optical

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class QrCameraAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader()
    private var lastScannedText = ""
    private var lastScannedTimestamp = 0L

    override fun analyze(image: ImageProxy) {
        val plane = image.planes.firstOrNull() ?: run {
            image.close()
            return
        }

        val buffer: ByteBuffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val width = image.width
        val height = image.height

        val source = PlanarYUVLuminanceSource(
            data,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            val result = reader.decodeWithState(binaryBitmap)
            val text = result.text
            val now = System.currentTimeMillis()

            if (text != null && (text != lastScannedText || now - lastScannedTimestamp > 150)) {
                lastScannedText = text
                lastScannedTimestamp = now
                onQrCodeScanned(text)
            }
        } catch (ignored: Exception) {
            // Frame did not contain a valid barcode
        } finally {
            reader.reset()
            image.close()
        }
    }
}
