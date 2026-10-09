package com.example.acoustic

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.example.protocol.CrcUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

enum class AcousticBand(
    val title: String,
    val subtitle: String,
    val markFreq: Double,
    val spaceFreq: Double,
    val syncFreq: Double
) {
    AUDIBLE(
        title = "الموجات المسموعة التكتيكية",
        subtitle = "Audible Cyber (1.8 kHz - 2.6 kHz) - موثوقية عالية جداً",
        markFreq = 2200.0,
        spaceFreq = 1800.0,
        syncFreq = 2600.0
    ),
    ULTRASONIC(
        title = "الموجات فوق الصوتية الصامتة",
        subtitle = "Near-Ultrasound Stealth (17.5 kHz - 19.5 kHz) - غير مسموعة للأذن",
        markFreq = 18500.0,
        spaceFreq = 17500.0,
        syncFreq = 19500.0
    )
}

data class AcousticTransmitterState(
    val isTransmitting: Boolean = false,
    val progress: Float = 0f,
    val currentPacketInfo: String = "",
    val totalBytesSent: Int = 0
)

data class AcousticReceiverState(
    val isListening: Boolean = false,
    val syncDetected: Boolean = false,
    val signalLevelDb: Float = -60f,
    val markEnergy: Float = 0f,
    val spaceEnergy: Float = 0f,
    val syncEnergy: Float = 0f,
    val lastDecodedText: String = "",
    val decodedBytesCount: Int = 0
)

class AcousticModem(private val scope: CoroutineScope) {

    private val sampleRate = 44100
    private val bitDurationMs = 28 // ~35 baud for high ambient noise tolerance
    private val samplesPerBit = (sampleRate * (bitDurationMs / 1000.0)).toInt()

    private val _txState = MutableStateFlow(AcousticTransmitterState())
    val txState = _txState.asStateFlow()

    private val _rxState = MutableStateFlow(AcousticReceiverState())
    val rxState = _rxState.asStateFlow()

    private val _liveWaveform = MutableStateFlow(FloatArray(128))
    val liveWaveform = _liveWaveform.asStateFlow()

    var activeBand: AcousticBand = AcousticBand.AUDIBLE

    private var txJob: Job? = null
    private var rxJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null
    private var activeAudioRecord: AudioRecord? = null

    // Callbacks
    var onPacketReceived: ((data: ByteArray, text: String) -> Unit)? = null

    /**
     * Synthesizes and plays Continuous-Phase FSK (CPFSK) audio for transmission
     */
    fun transmit(payload: ByteArray, info: String = "DATA PACKET") {
        if (_txState.value.isTransmitting) return

        txJob?.cancel()
        txJob = scope.launch(Dispatchers.Default) {
            _txState.value = AcousticTransmitterState(
                isTransmitting = true,
                progress = 0f,
                currentPacketInfo = info
            )

            try {
                // Build packet: [0x57, 0x4E] [Length: 1B] [Payload] [CRC16: 2B]
                val safeLength = minOf(payload.size, 250)
                val cleanPayload = payload.copyOfRange(0, safeLength)
                val crc16 = CrcUtils.calculateCrc16(cleanPayload)

                val packetBuffer = ByteBuffer.allocate(2 + 1 + safeLength + 2)
                packetBuffer.put(0x57.toByte()) // 'W'
                packetBuffer.put(0x4E.toByte()) // 'N'
                packetBuffer.put(safeLength.toByte())
                packetBuffer.put(cleanPayload)
                packetBuffer.putShort(crc16.toShort())
                val rawPacket = packetBuffer.array()

                // Generate audio samples
                val pcmSamples = generatePcmForPacket(rawPacket)

                // Play AudioTrack
                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBufSize, pcmSamples.size * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                activeAudioTrack = audioTrack
                audioTrack.play()

                val chunkSize = 2048
                var offset = 0
                val total = pcmSamples.size

                while (offset < total && isActive) {
                    val toWrite = minOf(chunkSize, total - offset)
                    audioTrack.write(pcmSamples, offset, toWrite)
                    offset += toWrite
                    val currentProgress = offset.toFloat() / total
                    _txState.value = _txState.value.copy(
                        progress = currentProgress,
                        totalBytesSent = (safeLength * currentProgress).toInt()
                    )
                }

                // Wait for playback to drain
                val drainWaitMs = ((total.toDouble() / sampleRate) * 1000).toLong()
                kotlinx.coroutines.delay(100)

                audioTrack.stop()
                audioTrack.release()
                activeAudioTrack = null

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _txState.value = AcousticTransmitterState(isTransmitting = false, progress = 1f)
            }
        }
    }

    private fun generatePcmForPacket(packet: ByteArray): ShortArray {
        val band = activeBand
        val syncPreambleSamples = (sampleRate * 0.18).toInt() // 180 ms sync chirp
        val guardSamples = (sampleRate * 0.04).toInt() // 40 ms guard band

        val totalBits = packet.size * 8
        val dataSamplesCount = totalBits * samplesPerBit
        val totalSamples = syncPreambleSamples + guardSamples + dataSamplesCount

        val samples = ShortArray(totalSamples)
        var sampleIndex = 0
        var phase = 0.0

        // 1. Preamble Sync Tone
        val syncDelta = 2.0 * PI * band.syncFreq / sampleRate
        for (i in 0 until syncPreambleSamples) {
            // Apply Hann envelope at edges to prevent clicking
            val envelope = when {
                i < 200 -> i / 200.0
                i > syncPreambleSamples - 200 -> (syncPreambleSamples - i) / 200.0
                else -> 1.0
            }
            samples[sampleIndex++] = (sin(phase) * 31000.0 * envelope).toInt().toShort()
            phase += syncDelta
        }

        // 2. Guard silence
        for (i in 0 until guardSamples) {
            samples[sampleIndex++] = 0
        }

        // 3. Modulate data bits with Continuous Phase Frequency Shift Keying (CPFSK)
        for (byte in packet) {
            for (bitIdx in 7 downTo 0) {
                val bit = ((byte.toInt() ushr bitIdx) and 1) == 1
                val freq = if (bit) band.markFreq else band.spaceFreq
                val deltaPhase = 2.0 * PI * freq / sampleRate

                for (s in 0 until samplesPerBit) {
                    // Cosine windowing per bit
                    samples[sampleIndex++] = (sin(phase) * 30000.0).toInt().toShort()
                    phase += deltaPhase
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                }
            }
        }

        return samples
    }

    /**
     * Starts listening on the device microphone and demodulates acoustic packets
     */
    fun startListening() {
        if (_rxState.value.isListening) return

        rxJob?.cancel()
        rxJob = scope.launch(Dispatchers.Default) {
            _rxState.value = _rxState.value.copy(isListening = true)

            val minBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioRecord = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBufSize, 4096)
                )
            } catch (e: SecurityException) {
                _rxState.value = _rxState.value.copy(isListening = false)
                return@launch
            }

            activeAudioRecord = audioRecord
            try {
                audioRecord.startRecording()
            } catch (e: Exception) {
                _rxState.value = _rxState.value.copy(isListening = false)
                return@launch
            }

            val readBuffer = ShortArray(samplesPerBit)
            val accumulatedBits = mutableListOf<Boolean>()
            var state = RxStateMachineState.LOOKING_FOR_SYNC
            var bitsNeeded = 0
            val visualizerWave = FloatArray(128)

            while (isActive && _rxState.value.isListening) {
                val read = audioRecord.read(readBuffer, 0, samplesPerBit)
                if (read <= 0) continue

                // 1. Calculate RMS amplitude in dB & update visualizer
                var sumSq = 0.0
                val step = maxOf(1, read / 128)
                for (i in 0 until 128) {
                    val sIdx = minOf(i * step, read - 1)
                    val sVal = readBuffer[sIdx]
                    visualizerWave[i] = sVal / 32768f
                    sumSq += sVal * sVal
                }
                _liveWaveform.value = visualizerWave.copyOf()

                val rms = sqrt(sumSq / read)
                val db = if (rms > 0.0) (20 * log10(rms / 32768.0)).toFloat() else -90f

                // 2. Compute frequency energy via Goertzel filters
                val band = activeBand
                val syncEnergy = goertzelMagnitude(readBuffer, read, band.syncFreq, sampleRate)
                val markEnergy = goertzelMagnitude(readBuffer, read, band.markFreq, sampleRate)
                val spaceEnergy = goertzelMagnitude(readBuffer, read, band.spaceFreq, sampleRate)

                _rxState.value = _rxState.value.copy(
                    signalLevelDb = db,
                    syncEnergy = syncEnergy,
                    markEnergy = markEnergy,
                    spaceEnergy = spaceEnergy
                )

                // 3. Demodulation state machine
                when (state) {
                    RxStateMachineState.LOOKING_FOR_SYNC -> {
                        // High energy in sync freq relative to ambient noise
                        if (syncEnergy > 1800f && syncEnergy > markEnergy * 1.5f && syncEnergy > spaceEnergy * 1.5f) {
                            _rxState.value = _rxState.value.copy(syncDetected = true)
                            state = RxStateMachineState.WAIT_GUARD
                        }
                    }

                    RxStateMachineState.WAIT_GUARD -> {
                        // Wait for guard interval (small silence or shift)
                        accumulatedBits.clear()
                        state = RxStateMachineState.READING_HEADER
                        bitsNeeded = 24 // 2 bytes magic ('WN') + 1 byte length = 24 bits
                    }

                    RxStateMachineState.READING_HEADER -> {
                        val bit = markEnergy >= spaceEnergy
                        accumulatedBits.add(bit)
                        if (accumulatedBits.size >= bitsNeeded) {
                            val headerBytes = bitsToBytes(accumulatedBits.take(bitsNeeded))
                            if (headerBytes.size >= 3 && headerBytes[0] == 0x57.toByte() && headerBytes[1] == 0x4E.toByte()) {
                                val length = headerBytes[2].toInt() and 0xFF
                                bitsNeeded = (3 + length + 2) * 8 // full packet bits
                                state = RxStateMachineState.READING_PAYLOAD
                            } else {
                                // False alarm or sync misalign
                                state = RxStateMachineState.LOOKING_FOR_SYNC
                                _rxState.value = _rxState.value.copy(syncDetected = false)
                            }
                        }
                    }

                    RxStateMachineState.READING_PAYLOAD -> {
                        val bit = markEnergy >= spaceEnergy
                        accumulatedBits.add(bit)
                        if (accumulatedBits.size >= bitsNeeded) {
                            val allBytes = bitsToBytes(accumulatedBits.take(bitsNeeded))
                            decodeAndVerifyPacket(allBytes)
                            state = RxStateMachineState.LOOKING_FOR_SYNC
                            _rxState.value = _rxState.value.copy(syncDetected = false)
                        }
                    }
                }
            }

            try {
                audioRecord.stop()
                audioRecord.release()
            } catch (ignored: Exception) {}
            activeAudioRecord = null
        }
    }

    private fun decodeAndVerifyPacket(packetBytes: ByteArray) {
        if (packetBytes.size < 5) return
        val length = packetBytes[2].toInt() and 0xFF
        if (packetBytes.size < 3 + length + 2) return

        val payload = packetBytes.copyOfRange(3, 3 + length)
        val expectedCrc = ByteBuffer.wrap(packetBytes, 3 + length, 2).short.toInt() and 0xFFFF
        val calculatedCrc = CrcUtils.calculateCrc16(payload)

        val isValid = (expectedCrc == calculatedCrc)
        val text = try {
            String(payload, Charsets.UTF_8)
        } catch (e: Exception) {
            "HEX: " + CrcUtils.toHex(payload)
        }

        if (isValid) {
            _rxState.value = _rxState.value.copy(
                lastDecodedText = text,
                decodedBytesCount = _rxState.value.decodedBytesCount + payload.size
            )
            onPacketReceived?.invoke(payload, text)
        }
    }

    fun stopListening() {
        rxJob?.cancel()
        _rxState.value = _rxState.value.copy(isListening = false, syncDetected = false)
        try {
            activeAudioRecord?.stop()
            activeAudioRecord?.release()
        } catch (ignored: Exception) {}
        activeAudioRecord = null
    }

    fun stopTransmission() {
        txJob?.cancel()
        _txState.value = AcousticTransmitterState(isTransmitting = false)
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (ignored: Exception) {}
        activeAudioTrack = null
    }

    /**
     * Goertzel algorithm computes discrete Fourier component magnitude at target frequency
     */
    private fun goertzelMagnitude(samples: ShortArray, numSamples: Int, targetFreq: Double, sampleRate: Int): Float {
        val k = (0.5 + ((numSamples * targetFreq) / sampleRate)).toInt()
        val omega = (2.0 * PI * k) / numSamples
        val coeff = 2.0 * cos(omega)

        var q0: Double
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until numSamples) {
            val sample = samples[i].toDouble()
            q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }

        val magnitudeSq = (q1 * q1) + (q2 * q2) - (q1 * q2 * coeff)
        return (sqrt(magnitudeSq) / numSamples).toFloat()
    }

    private fun bitsToBytes(bits: List<Boolean>): ByteArray {
        val numBytes = bits.size / 8
        val result = ByteArray(numBytes)
        for (i in 0 until numBytes) {
            var b = 0
            for (bitIdx in 0 until 8) {
                if (bits[i * 8 + bitIdx]) {
                    b = b or (1 shl (7 - bitIdx))
                }
            }
            result[i] = b.toByte()
        }
        return result
    }

    private enum class RxStateMachineState {
        LOOKING_FOR_SYNC,
        WAIT_GUARD,
        READING_HEADER,
        READING_PAYLOAD
    }
}
