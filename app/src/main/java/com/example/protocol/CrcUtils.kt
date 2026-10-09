package com.example.protocol

import java.util.zip.CRC32

object CrcUtils {
    fun calculateCrc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    fun calculateCrc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0 until 8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    fun toHex(bytes: ByteArray, maxBytes: Int = 32): String {
        val count = minOf(bytes.size, maxBytes)
        val sb = StringBuilder()
        for (i in 0 until count) {
            sb.append(String.format("%02X ", bytes[i]))
        }
        if (bytes.size > maxBytes) {
            sb.append("... [${bytes.size} bytes]")
        }
        return sb.toString().trim()
    }
}
