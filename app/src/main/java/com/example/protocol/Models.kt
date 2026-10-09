package com.example.protocol

import java.io.File

enum class TransmissionMedium(val displayName: String, val badgeColorHex: Long) {
    ACOUSTIC("أصوات الموجات (Acoustic)", 0xFF00F0FF),
    OPTICAL_QR("باركود متدفق (Optical QR)", 0xFFFFB703),
    NEXUS_6G("شبكة 6G المباشرة (Nexus-6G P2P)", 0xFF06D6A0)
}

enum class PacketType(val code: Byte, val label: String) {
    HANDSHAKE(0x01, "HANDSHAKE"),
    HEARTBEAT(0x02, "HEARTBEAT"),
    TELEMETRY(0x03, "TELEMETRY"),
    MESSAGE(0x10, "MESSAGE"),
    FILE_MANIFEST(0x20, "FILE_MANIFEST"),
    FILE_CHUNK(0x21, "FILE_CHUNK"),
    FILE_ACK(0x22, "FILE_ACK"),
    FILE_COMPLETE(0x23, "FILE_COMPLETE")
}

data class TransferredFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val medium: TransmissionMedium,
    val timestamp: Long = System.currentTimeMillis(),
    val localFile: File? = null,
    val textContent: String? = null
) {
    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
            else -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
        }
}

data class ProtocolPacketLog(
    val id: String = java.util.UUID.randomUUID().toString().substring(0, 8),
    val timestamp: Long = System.currentTimeMillis(),
    val medium: TransmissionMedium,
    val direction: Direction,
    val summary: String,
    val payloadLength: Int,
    val hexSnippet: String,
    val isCrcValid: Boolean = true
) {
    enum class Direction { OUTGOING, INCOMING }
}

data class LinkTelemetry(
    val isConnected: Boolean = false,
    val peerIp: String = "0.0.0.0",
    val pingMs: Long = 0,
    val throughputBytesPerSec: Long = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val totalBytesTransferred: Long = 0
) {
    val formattedSpeed: String
        get() {
            val speedMb = throughputBytesPerSec / (1024.0 * 1024.0)
            return if (speedMb >= 0.1) String.format("%.2f MB/s", speedMb)
            else String.format("%.1f KB/s", throughputBytesPerSec / 1024.0)
        }
}
