package com.example

import com.example.optical.OpticalFrame
import com.example.protocol.CrcUtils
import com.example.protocol.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun crc16_and_crc32_computationIsCorrect() {
        val testBytes = "WAVENET-6G-PROTOCOL".toByteArray(Charsets.UTF_8)
        val crc32 = CrcUtils.calculateCrc32(testBytes)
        val crc16 = CrcUtils.calculateCrc16(testBytes)

        assertTrue(crc32 > 0)
        assertTrue(crc16 in 0..0xFFFF)

        val hex = CrcUtils.toHex(testBytes, 5)
        assertTrue(hex.isNotEmpty())
    }

    @Test
    fun opticalFrame_serializationAndParsing_succeeds() {
        val payload = "HELLO_ACOUSTIC_AND_BARCODE".toByteArray(Charsets.UTF_8)
        val crc = CrcUtils.calculateCrc32(payload)
        val frame = OpticalFrame(
            fileId = "A1F9",
            chunkIndex = 1,
            totalChunks = 5,
            fileName = "data.bin",
            chunkCrc = crc,
            dataBytes = payload
        )

        val serialized = frame.toSerializedString()
        assertTrue(serialized.startsWith("W6G|A1F9|1|5|data.bin|$crc|"))

        val parsed = OpticalFrame.parse(serialized)
        assertNotNull(parsed)
        assertEquals(frame.fileId, parsed!!.fileId)
        assertEquals(frame.chunkIndex, parsed.chunkIndex)
        assertEquals(frame.totalChunks, parsed.totalChunks)
        assertEquals(frame.fileName, parsed.fileName)
        assertEquals(frame.chunkCrc, parsed.chunkCrc)
        assertEquals(String(payload), String(parsed.dataBytes))
    }

    @Test
    fun packetType_opcodes_match() {
        assertEquals(0x01.toByte(), PacketType.HANDSHAKE.code)
        assertEquals(0x02.toByte(), PacketType.HEARTBEAT.code)
        assertEquals(0x10.toByte(), PacketType.MESSAGE.code)
        assertEquals(0x21.toByte(), PacketType.FILE_CHUNK.code)
    }
}
