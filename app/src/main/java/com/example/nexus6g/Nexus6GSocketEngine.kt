package com.example.nexus6g

import android.content.Context
import android.net.wifi.WifiManager
import com.example.protocol.CrcUtils
import com.example.protocol.LinkTelemetry
import com.example.protocol.PacketType
import com.example.protocol.TransferredFile
import com.example.protocol.TransmissionMedium
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Collections
import java.util.UUID

enum class NexusRole {
    GATEWAY_HOST, // الخادم / المحطة المضيفة
    PEER_CLIENT   // النظير المتصل
}

data class NexusChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromSelf: Boolean
)

data class NexusTransferProgress(
    val isTransferring: Boolean = false,
    val fileName: String = "",
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val isOutgoing: Boolean = true
) {
    val progressFloat: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val formattedPercent: String
        get() = "${(progressFloat * 100).toInt()}%"
}

class Nexus6GSocketEngine(
    private val scope: CoroutineScope,
    private val context: Context,
    private val cacheDir: File
) {
    val port = 8888

    private val _telemetry = MutableStateFlow(LinkTelemetry())
    val telemetry = _telemetry.asStateFlow()

    private val _messages = MutableStateFlow<List<NexusChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _transferProgress = MutableStateFlow(NexusTransferProgress())
    val transferProgress = _transferProgress.asStateFlow()

    private val _currentRole = MutableStateFlow(NexusRole.GATEWAY_HOST)
    val currentRole = _currentRole.asStateFlow()

    private val _localIpAddress = MutableStateFlow(getLocalIp())
    val localIpAddress = _localIpAddress.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null

    private var engineJob: Job? = null
    private var heartbeatJob: Job? = null

    var onFileReceived: ((TransferredFile) -> Unit)? = null
    var onPacketLogged: ((summary: String, length: Int, bytes: ByteArray) -> Unit)? = null

    fun refreshIp() {
        _localIpAddress.value = getLocalIp()
    }

    /**
     * Start as 6G Gateway Node (Host Server)
     */
    fun startGateway() {
        stop()
        _currentRole.value = NexusRole.GATEWAY_HOST
        refreshIp()

        engineJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(port)
                serverSocket = server
                _telemetry.value = _telemetry.value.copy(
                    isConnected = false,
                    peerIp = "في انتظار اتصال النظير (Listening on $port)..."
                )

                while (isActive && !server.isClosed) {
                    val socket = server.accept()
                    socket.tcpNoDelay = true
                    activeSocket = socket
                    val peerAddr = socket.inetAddress.hostAddress ?: "Peer"

                    _telemetry.value = _telemetry.value.copy(
                        isConnected = true,
                        peerIp = peerAddr
                    )

                    handleSocketConnection(socket)
                }
            } catch (e: Exception) {
                if (isActive) {
                    _telemetry.value = _telemetry.value.copy(isConnected = false)
                }
            }
        }
    }

    /**
     * Connect as 6G Peer Node (Client) to Gateway Host
     */
    fun connectAsPeer(targetHost: String) {
        stop()
        _currentRole.value = NexusRole.PEER_CLIENT
        refreshIp()

        engineJob = scope.launch(Dispatchers.IO) {
            try {
                _telemetry.value = _telemetry.value.copy(
                    isConnected = false,
                    peerIp = "جار الاتصال بـ $targetHost:$port..."
                )

                val socket = Socket(targetHost, port)
                socket.tcpNoDelay = true
                activeSocket = socket

                _telemetry.value = _telemetry.value.copy(
                    isConnected = true,
                    peerIp = targetHost
                )

                // Send Handshake
                sendPacket(PacketType.HANDSHAKE.code, "NEXUS_6G_PEER_HELLO".toByteArray())

                handleSocketConnection(socket)
            } catch (e: Exception) {
                _telemetry.value = _telemetry.value.copy(
                    isConnected = false,
                    peerIp = "فشل الاتصال: ${e.message}"
                )
            }
        }
    }

    private suspend fun handleSocketConnection(socket: Socket) = withContext(Dispatchers.IO) {
        val inStream = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val outStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        inputStream = inStream
        outputStream = outStream

        startHeartbeat()

        var incomingFileStream: FileOutputStream? = null
        var incomingFileTotalSize = 0L
        var incomingFileTransferred = 0L
        var incomingFileName = ""
        var incomingFileTemp: File? = null

        val speedCalcBuffer = mutableListOf<Pair<Long, Long>>() // timestamp to bytes

        try {
            while (isActive && socket.isConnected && !socket.isClosed) {
                // Header format: Magic(2B) 'NX' (0x4E, 0x58) + Version(1B) + OpCode(1B) + Length(4B)
                val m1 = inStream.readByte()
                val m2 = inStream.readByte()
                if (m1 != 0x4E.toByte() || m2 != 0x58.toByte()) {
                    continue // Framing desync, hunt for magic
                }

                val version = inStream.readByte()
                val opCode = inStream.readByte()
                val payloadLen = inStream.readInt()

                if (payloadLen < 0 || payloadLen > 10 * 1024 * 1024) continue // Safety limit

                val payload = ByteArray(payloadLen)
                inStream.readFully(payload)

                val crc = inStream.readShort().toInt() and 0xFFFF
                val calcCrc = CrcUtils.calculateCrc16(payload)
                val isCrcOk = (crc == calcCrc)

                if (!isCrcOk) continue

                // Update Telemetry
                val now = System.currentTimeMillis()
                val totalRxBytes = _telemetry.value.totalBytesTransferred + payloadLen + 10
                speedCalcBuffer.add(Pair(now, payloadLen.toLong()))
                speedCalcBuffer.removeAll { now - it.first > 1000 }
                val currentSpeed = speedCalcBuffer.sumOf { it.second }

                _telemetry.value = _telemetry.value.copy(
                    packetsReceived = _telemetry.value.packetsReceived + 1,
                    totalBytesTransferred = totalRxBytes,
                    throughputBytesPerSec = currentSpeed
                )

                onPacketLogged?.invoke("6G IN: OpCode 0x${Integer.toHexString(opCode.toInt())}", payloadLen, payload)

                // Dispatch OpCodes
                when (opCode) {
                    PacketType.HANDSHAKE.code -> {
                        sendPacket(PacketType.HANDSHAKE.code, "NEXUS_6G_ACK_READY".toByteArray())
                    }

                    PacketType.HEARTBEAT.code -> {
                        // Ping response: Payload contains timestamp sent
                        if (payload.size >= 8) {
                            val sentTime = ByteBuffer.wrap(payload).long
                            val rtt = (System.currentTimeMillis() - sentTime).coerceAtLeast(1)
                            _telemetry.value = _telemetry.value.copy(pingMs = rtt)
                        }
                    }

                    PacketType.MESSAGE.code -> {
                        val text = String(payload, Charsets.UTF_8)
                        val msg = NexusChatMessage(
                            sender = "النظير (Peer)",
                            text = text,
                            isFromSelf = false
                        )
                        _messages.value = _messages.value + msg
                    }

                    PacketType.FILE_MANIFEST.code -> {
                        // format: name|size
                        val manifestStr = String(payload, Charsets.UTF_8)
                        val parts = manifestStr.split("|")
                        if (parts.size >= 2) {
                            incomingFileName = parts[0]
                            incomingFileTotalSize = parts[1].toLongOrNull() ?: 0L
                            incomingFileTransferred = 0L
                            incomingFileTemp = File(cacheDir, "nexus6g_${System.currentTimeMillis()}_$incomingFileName")
                            incomingFileStream = FileOutputStream(incomingFileTemp)

                            _transferProgress.value = NexusTransferProgress(
                                isTransferring = true,
                                fileName = incomingFileName,
                                totalBytes = incomingFileTotalSize,
                                transferredBytes = 0L,
                                isOutgoing = false
                            )

                            sendPacket(PacketType.FILE_ACK.code, "ACCEPT".toByteArray())
                        }
                    }

                    PacketType.FILE_CHUNK.code -> {
                        incomingFileStream?.write(payload)
                        incomingFileTransferred += payload.size

                        _transferProgress.value = _transferProgress.value.copy(
                            transferredBytes = incomingFileTransferred
                        )
                    }

                    PacketType.FILE_COMPLETE.code -> {
                        try {
                            incomingFileStream?.flush()
                            incomingFileStream?.close()
                        } catch (e: Exception) {}
                        incomingFileStream = null

                        _transferProgress.value = NexusTransferProgress(isTransferring = false)

                        incomingFileTemp?.let { file ->
                            val tf = TransferredFile(
                                id = UUID.randomUUID().toString().substring(0, 8),
                                name = incomingFileName,
                                sizeBytes = file.length(),
                                mimeType = "application/octet-stream",
                                medium = TransmissionMedium.NEXUS_6G,
                                localFile = file
                            )
                            onFileReceived?.invoke(tf)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Disconnected
        } finally {
            try {
                incomingFileStream?.close()
            } catch (ignored: Exception) {}
            _telemetry.value = _telemetry.value.copy(isConnected = false)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && _telemetry.value.isConnected) {
                delay(2000)
                try {
                    val bb = ByteBuffer.allocate(8)
                    bb.putLong(System.currentTimeMillis())
                    sendPacket(PacketType.HEARTBEAT.code, bb.array())
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val msg = NexusChatMessage(sender = "أنت (Self)", text = text, isFromSelf = true)
        _messages.value = _messages.value + msg

        scope.launch(Dispatchers.IO) {
            sendPacket(PacketType.MESSAGE.code, text.toByteArray(Charsets.UTF_8))
        }
    }

    fun sendFile(file: File) {
        if (!file.exists() || !_telemetry.value.isConnected) return

        scope.launch(Dispatchers.IO) {
            try {
                val totalSize = file.length()
                val manifest = "${file.name}|$totalSize"
                _transferProgress.value = NexusTransferProgress(
                    isTransferring = true,
                    fileName = file.name,
                    totalBytes = totalSize,
                    transferredBytes = 0L,
                    isOutgoing = true
                )

                // 1. Send Manifest
                sendPacket(PacketType.FILE_MANIFEST.code, manifest.toByteArray(Charsets.UTF_8))
                delay(100)

                // 2. Stream chunks (64KB chunks for high speed)
                val fis = FileInputStream(file)
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var sentBytes = 0L

                while (fis.read(buffer).also { bytesRead = it } != -1 && isActive) {
                    val chunk = buffer.copyOfRange(0, bytesRead)
                    sendPacket(PacketType.FILE_CHUNK.code, chunk)
                    sentBytes += bytesRead

                    _transferProgress.value = _transferProgress.value.copy(
                        transferredBytes = sentBytes
                    )
                }
                fis.close()

                // 3. Complete
                sendPacket(PacketType.FILE_COMPLETE.code, "DONE".toByteArray())
                delay(200)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _transferProgress.value = NexusTransferProgress(isTransferring = false)
            }
        }
    }

    private fun sendPacket(opCode: Byte, payload: ByteArray) {
        val out = outputStream ?: return
        try {
            synchronized(out) {
                // Header: 'N' 'X' + Version(1B) + OpCode(1B) + Length(4B)
                out.writeByte(0x4E)
                out.writeByte(0x58)
                out.writeByte(0x01)
                out.writeByte(opCode.toInt())
                out.writeInt(payload.size)
                out.write(payload)

                val crc = CrcUtils.calculateCrc16(payload)
                out.writeShort(crc)
                out.flush()
            }

            _telemetry.value = _telemetry.value.copy(
                packetsSent = _telemetry.value.packetsSent + 1,
                totalBytesTransferred = _telemetry.value.totalBytesTransferred + payload.size + 10
            )

            onPacketLogged?.invoke("6G OUT: OpCode 0x${Integer.toHexString(opCode.toInt())}", payload.size, payload)
        } catch (e: Exception) {
            // Socket write failed
        }
    }

    fun stop() {
        engineJob?.cancel()
        heartbeatJob?.cancel()
        try {
            activeSocket?.close()
        } catch (ignored: Exception) {}
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        activeSocket = null
        serverSocket = null
        inputStream = null
        outputStream = null
        _telemetry.value = LinkTelemetry()
        _transferProgress.value = NexusTransferProgress()
    }

    private fun getLocalIp(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.indexOf(':') < 0 && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            return ip
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return "192.168.43.1"
    }
}
