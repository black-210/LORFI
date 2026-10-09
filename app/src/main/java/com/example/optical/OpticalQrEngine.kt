package com.example.optical

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.example.protocol.CrcUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object Base64Compat {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i++].toInt() and 0xFF
            val b1 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1

            val c0 = b0 ushr 2
            val c1 = ((b0 and 3) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)
            val c2 = if (b1 >= 0) ((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0) else -1
            val c3 = if (b2 >= 0) b2 and 0x3F else -1

            sb.append(CHARS[c0])
            sb.append(CHARS[c1])
            sb.append(if (c2 >= 0) CHARS[c2] else '=')
            sb.append(if (c3 >= 0) CHARS[c3] else '=')
        }
        return sb.toString()
    }

    fun decode(str: String): ByteArray {
        val clean = str.trim().replace("=", "")
        val out = ByteArrayOutputStream(clean.length * 3 / 4)
        var i = 0
        while (i < clean.length) {
            val c0 = CHARS.indexOf(clean[i++])
            val c1 = if (i < clean.length) CHARS.indexOf(clean[i++]) else 0
            val c2 = if (i < clean.length) CHARS.indexOf(clean[i++]) else -1
            val c3 = if (i < clean.length) CHARS.indexOf(clean[i++]) else -1

            if (c0 >= 0 && c1 >= 0) {
                out.write((c0 shl 2) or (c1 ushr 4))
            }
            if (c2 >= 0) {
                out.write(((c1 and 0x0F) shl 4) or (c2 ushr 2))
            }
            if (c3 >= 0) {
                out.write(((c2 and 0x03) shl 6) or c3)
            }
        }
        return out.toByteArray()
    }
}

data class OpticalFrame(
    val fileId: String,
    val chunkIndex: Int, // 1-based
    val totalChunks: Int,
    val fileName: String,
    val chunkCrc: Long,
    val dataBytes: ByteArray
) {
    fun toSerializedString(): String {
        val b64 = Base64Compat.encode(dataBytes)
        return "W6G|$fileId|$chunkIndex|$totalChunks|$fileName|$chunkCrc|$b64"
    }

    companion object {
        fun parse(raw: String): OpticalFrame? {
            if (!raw.startsWith("W6G|")) return null
            val parts = raw.split("|")
            if (parts.size < 7) return null

            return try {
                val fileId = parts[1]
                val chunkIndex = parts[2].toInt()
                val totalChunks = parts[3].toInt()
                val fileName = parts[4]
                val chunkCrc = parts[5].toLong()
                val rawBytes = Base64Compat.decode(parts[6])

                // Verify CRC32
                val actualCrc = CrcUtils.calculateCrc32(rawBytes)
                if (actualCrc != chunkCrc) return null

                OpticalFrame(fileId, chunkIndex, totalChunks, fileName, chunkCrc, rawBytes)
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class OpticalTxState(
    val isStreaming: Boolean = false,
    val currentFrameIndex: Int = 0,
    val totalFrames: Int = 0,
    val currentFps: Int = 4,
    val fileId: String = "",
    val fileName: String = "",
    val currentBitmap: Bitmap? = null
)

data class OpticalRxState(
    val isReceiving: Boolean = false,
    val fileId: String = "",
    val fileName: String = "",
    val totalChunks: Int = 0,
    val receivedCount: Int = 0,
    val missingChunks: List<Int> = emptyList(),
    val receivedChunkIndices: Set<Int> = emptySet(),
    val isComplete: Boolean = false,
    val completedFile: File? = null,
    val completedText: String? = null,
    val lastScannedInfo: String = ""
)

class OpticalQrEngine(
    private val scope: CoroutineScope,
    private val cacheDir: File
) {
    private val qrWriter = QRCodeWriter()
    private val qrHints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1
    )

    private val _txState = MutableStateFlow(OpticalTxState())
    val txState = _txState.asStateFlow()

    private val _rxState = MutableStateFlow(OpticalRxState())
    val rxState = _rxState.asStateFlow()

    private var framesList = listOf<OpticalFrame>()
    private var streamJob: Job? = null

    // Chunk size: 140 bytes of raw data yields ~190 chars of Base64, easily fits in standard Version 4-6 QR
    private val chunkSize = 140

    fun prepareStream(bytes: ByteArray, fileName: String = "data.bin") {
        streamJob?.cancel()
        val fileId = UUID.randomUUID().toString().substring(0, 4).uppercase()
        val totalChunks = maxOf(1, (bytes.size + chunkSize - 1) / chunkSize)

        val frames = mutableListOf<OpticalFrame>()
        var offset = 0
        var chunkIndex = 1

        while (offset < bytes.size || (bytes.isEmpty() && chunkIndex == 1)) {
            val length = minOf(chunkSize, bytes.size - offset)
            val chunkBytes = if (bytes.isEmpty()) ByteArray(0) else bytes.copyOfRange(offset, offset + length)
            val crc = CrcUtils.calculateCrc32(chunkBytes)
            frames.add(OpticalFrame(fileId, chunkIndex, totalChunks, fileName, crc, chunkBytes))
            offset += length
            chunkIndex++
        }

        framesList = frames
        _txState.value = OpticalTxState(
            isStreaming = false,
            currentFrameIndex = 0,
            totalFrames = frames.size,
            fileId = fileId,
            fileName = fileName,
            currentBitmap = generateQrBitmap(frames.firstOrNull()?.toSerializedString() ?: "")
        )
    }

    fun startStreaming(fps: Int = _txState.value.currentFps) {
        if (framesList.isEmpty()) return
        streamJob?.cancel()
        _txState.value = _txState.value.copy(isStreaming = true, currentFps = fps)

        streamJob = scope.launch(Dispatchers.Default) {
            var index = _txState.value.currentFrameIndex
            while (isActive && _txState.value.isStreaming) {
                val frame = framesList[index % framesList.size]
                val bmp = generateQrBitmap(frame.toSerializedString())

                _txState.value = _txState.value.copy(
                    currentFrameIndex = (index % framesList.size) + 1,
                    currentBitmap = bmp
                )

                val delayMs = (1000 / _txState.value.currentFps).toLong()
                delay(delayMs)
                index++
            }
        }
    }

    fun pauseStreaming() {
        streamJob?.cancel()
        _txState.value = _txState.value.copy(isStreaming = false)
    }

    fun nextFrame() {
        if (framesList.isEmpty()) return
        val nextIdx = (_txState.value.currentFrameIndex % framesList.size)
        val frame = framesList[nextIdx]
        val bmp = generateQrBitmap(frame.toSerializedString())
        _txState.value = _txState.value.copy(
            currentFrameIndex = nextIdx + 1,
            currentBitmap = bmp
        )
    }

    fun previousFrame() {
        if (framesList.isEmpty()) return
        var prevIdx = _txState.value.currentFrameIndex - 2
        if (prevIdx < 0) prevIdx = framesList.size - 1
        val frame = framesList[prevIdx]
        val bmp = generateQrBitmap(frame.toSerializedString())
        _txState.value = _txState.value.copy(
            currentFrameIndex = prevIdx + 1,
            currentBitmap = bmp
        )
    }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        if (content.isEmpty()) {
            val empty = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            empty.eraseColor(Color.WHITE)
            return empty
        }
        return try {
            val bitMatrix = qrWriter.encode(content, BarcodeFormat.QR_CODE, size, size, qrHints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            val errBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            errBitmap.eraseColor(Color.LTGRAY)
            errBitmap
        }
    }

    // --- RECEIVER LOGIC ---
    private val receivedChunksMap = mutableMapOf<Int, ByteArray>()

    fun resetReceiver() {
        receivedChunksMap.clear()
        _rxState.value = OpticalRxState()
    }

    fun processScannedQrString(raw: String): Boolean {
        val frame = OpticalFrame.parse(raw) ?: return false

        // Check if new file or same file
        if (_rxState.value.fileId.isNotEmpty() && _rxState.value.fileId != frame.fileId && !_rxState.value.isComplete) {
            // New transmission detected
            receivedChunksMap.clear()
        }

        receivedChunksMap[frame.chunkIndex] = frame.dataBytes
        val receivedCount = receivedChunksMap.size
        val total = frame.totalChunks

        val missing = mutableListOf<Int>()
        for (i in 1..total) {
            if (!receivedChunksMap.containsKey(i)) {
                missing.add(i)
            }
        }

        val isComplete = (receivedCount >= total)

        var completedFile: File? = null
        var completedText: String? = null

        if (isComplete) {
            // Reassemble
            val bos = ByteArrayOutputStream()
            for (i in 1..total) {
                receivedChunksMap[i]?.let { bos.write(it) }
            }
            val completeBytes = bos.toByteArray()

            val outFile = File(cacheDir, "received_${System.currentTimeMillis()}_${frame.fileName}")
            try {
                outFile.writeBytes(completeBytes)
                completedFile = outFile
                completedText = try {
                    String(completeBytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _rxState.value = OpticalRxState(
            isReceiving = !isComplete,
            fileId = frame.fileId,
            fileName = frame.fileName,
            totalChunks = total,
            receivedCount = receivedCount,
            missingChunks = missing,
            receivedChunkIndices = receivedChunksMap.keys.toSet(),
            isComplete = isComplete,
            completedFile = completedFile,
            completedText = completedText,
            lastScannedInfo = "حزمة: ${frame.chunkIndex}/$total (${frame.dataBytes.size} بايت)"
        )

        return true
    }

    fun generateMissingChunksNackQr(): Bitmap? {
        val missing = _rxState.value.missingChunks
        val fileId = _rxState.value.fileId
        if (missing.isEmpty() || fileId.isEmpty()) return null
        val payload = "W6G_NACK|$fileId|" + missing.joinToString(",")
        return generateQrBitmap(payload, 380)
    }
}
