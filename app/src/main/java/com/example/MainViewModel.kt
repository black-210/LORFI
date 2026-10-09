package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.acoustic.AcousticBand
import com.example.acoustic.AcousticModem
import com.example.nexus6g.Nexus6GSocketEngine
import com.example.nexus6g.NexusRole
import com.example.optical.OpticalQrEngine
import com.example.protocol.CrcUtils
import com.example.protocol.ProtocolPacketLog
import com.example.protocol.TransferredFile
import com.example.protocol.TransmissionMedium
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cacheDir: File = application.cacheDir

    val acousticModem = AcousticModem(viewModelScope)
    val opticalEngine = OpticalQrEngine(viewModelScope, cacheDir)
    val nexus6gEngine = Nexus6GSocketEngine(viewModelScope, application.applicationContext, cacheDir)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _packetLogs = MutableStateFlow<List<ProtocolPacketLog>>(emptyList())
    val packetLogs = _packetLogs.asStateFlow()

    private val _receivedFiles = MutableStateFlow<List<TransferredFile>>(emptyList())
    val receivedFiles = _receivedFiles.asStateFlow()

    private val _userFeedback = MutableStateFlow<String?>(null)
    val userFeedback = _userFeedback.asStateFlow()

    init {
        // Acoustic callback
        acousticModem.onPacketReceived = { data, text ->
            logPacket(
                medium = TransmissionMedium.ACOUSTIC,
                direction = ProtocolPacketLog.Direction.INCOMING,
                summary = "حزمة صوتية تم فك تشفيرها بنجاح: $text",
                payload = data
            )
            // Save file record
            val fileRecord = TransferredFile(
                id = UUID.randomUUID().toString().substring(0, 8),
                name = "Acoustic_Packet_${System.currentTimeMillis()}.txt",
                sizeBytes = data.size.toLong(),
                mimeType = "text/plain",
                medium = TransmissionMedium.ACOUSTIC,
                textContent = text
            )
            addReceivedFile(fileRecord)
        }

        // 6G file callback
        nexus6gEngine.onFileReceived = { file ->
            logPacket(
                medium = TransmissionMedium.NEXUS_6G,
                direction = ProtocolPacketLog.Direction.INCOMING,
                summary = "ملف مكتمل عبر بروتوكول 6G: ${file.name} (${file.formattedSize})",
                payload = ByteArray(0)
            )
            addReceivedFile(file)
        }

        nexus6gEngine.onPacketLogged = { summary, length, bytes ->
            val dir = if (summary.contains("IN")) ProtocolPacketLog.Direction.INCOMING else ProtocolPacketLog.Direction.OUTGOING
            logPacket(
                medium = TransmissionMedium.NEXUS_6G,
                direction = dir,
                summary = summary,
                payload = bytes
            )
        }

        // Prepare default optical stream payload
        val initialDemoText = "NEXUS-6G TACTICAL PROTOCOL BURST :: SECURE OFFLINE AIR-GAP TRANSMISSION :: STATUS=OK"
        opticalEngine.prepareStream(initialDemoText.toByteArray(Charsets.UTF_8), "manifest.txt")
    }

    fun setTab(index: Int) {
        _selectedTab.value = index
    }

    fun clearFeedback() {
        _userFeedback.value = null
    }

    fun showFeedback(msg: String) {
        _userFeedback.value = msg
    }

    fun logPacket(
        medium: TransmissionMedium,
        direction: ProtocolPacketLog.Direction,
        summary: String,
        payload: ByteArray
    ) {
        val log = ProtocolPacketLog(
            medium = medium,
            direction = direction,
            summary = summary,
            payloadLength = payload.size,
            hexSnippet = CrcUtils.toHex(payload, 24)
        )
        _packetLogs.value = listOf(log) + _packetLogs.value.take(50)
    }

    private fun addReceivedFile(file: TransferredFile) {
        _receivedFiles.value = listOf(file) + _receivedFiles.value
    }

    // --- Acoustic Actions ---
    fun transmitAcousticText(text: String) {
        if (text.isBlank()) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        logPacket(
            medium = TransmissionMedium.ACOUSTIC,
            direction = ProtocolPacketLog.Direction.OUTGOING,
            summary = "بث صوتي: $text",
            payload = bytes
        )
        acousticModem.transmit(bytes, "TEXT: $text")
    }

    // --- Optical Actions ---
    fun prepareOpticalText(text: String, filename: String = "data.txt") {
        val bytes = text.toByteArray(Charsets.UTF_8)
        opticalEngine.prepareStream(bytes, filename)
        logPacket(
            medium = TransmissionMedium.OPTICAL_QR,
            direction = ProtocolPacketLog.Direction.OUTGOING,
            summary = "تجهيز بث باركود متدفق (${bytes.size} بايت)",
            payload = bytes
        )
    }

    fun prepareOpticalFile(file: File) {
        if (!file.exists()) return
        val bytes = file.readBytes()
        opticalEngine.prepareStream(bytes, file.name)
        logPacket(
            medium = TransmissionMedium.OPTICAL_QR,
            direction = ProtocolPacketLog.Direction.OUTGOING,
            summary = "تجهيز بث باركود متدفق للملف ${file.name} (${bytes.size} بايت)",
            payload = bytes
        )
    }

    fun onScannedBarcode(raw: String) {
        val accepted = opticalEngine.processScannedQrString(raw)
        if (accepted) {
            val rxState = opticalEngine.rxState.value
            logPacket(
                medium = TransmissionMedium.OPTICAL_QR,
                direction = ProtocolPacketLog.Direction.INCOMING,
                summary = "التقاط حزمة باركود ضوئي: ${rxState.lastScannedInfo}",
                payload = raw.toByteArray(Charsets.UTF_8)
            )

            if (rxState.isComplete && rxState.completedFile != null) {
                val tf = TransferredFile(
                    id = UUID.randomUUID().toString().substring(0, 8),
                    name = rxState.fileName,
                    sizeBytes = rxState.completedFile.length(),
                    mimeType = "application/octet-stream",
                    medium = TransmissionMedium.OPTICAL_QR,
                    localFile = rxState.completedFile,
                    textContent = rxState.completedText
                )
                addReceivedFile(tf)
                showFeedback("تم استلام الملف كاملاً بالباركود المتدفق بنجاح!")
            }
        }
    }

    // Quick Test helper: writes a dummy file for testing transfer
    fun createSampleTestFile(name: String, sizeKb: Int): File {
        val f = File(cacheDir, name)
        val dummyData = ByteArray(sizeKb * 1024) { (it % 256).toByte() }
        f.writeBytes(dummyData)
        return f
    }

    override fun onCleared() {
        super.onCleared()
        acousticModem.stopListening()
        acousticModem.stopTransmission()
        opticalEngine.pauseStreaming()
        nexus6gEngine.stop()
    }
}
