package com.example.mbdictate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavChunkerTest {
    @Test
    fun mergesOverlappingTranscripts() {
        assertEquals(
            "hello world this is a test",
            WavChunker.mergeTranscripts(
                listOf("hello world this is", "this is a test"),
            ),
        )
    }

    @Test
    fun keepsNonOverlappingTranscripts() {
        assertEquals(
            "hello world",
            WavChunker.mergeTranscripts(listOf("hello", "world")),
        )
    }

    @Test
    fun returnsOriginalWhenAudioFits() {
        val wav = wav(seconds = 2)
        val chunks = WavChunker.chunkWav(wav, maxSeconds = 3)
        assertEquals(1, chunks.size)
        assertTrue(chunks.first().contentEquals(wav))
    }

    @Test
    fun splitsLongAudioIntoValidWavFiles() {
        val wav = wav(seconds = 7)
        val chunks = WavChunker.chunkWav(wav, maxSeconds = 3, overlapSeconds = 0.5)
        assertTrue(chunks.size > 1)
        for (chunk in chunks) {
            assertEquals("RIFF", String(chunk, 0, 4))
            assertEquals("WAVE", String(chunk, 8, 4))
            assertEquals("data", String(chunk, 36, 4))
            val buffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(chunk.size - 8, buffer.getInt(4))
            assertEquals(chunk.size - 44, buffer.getInt(40))
        }
    }

    private fun wav(seconds: Int): ByteArray {
        val sampleRate = 16000
        val channels = 1
        val bits = 16
        val dataSize = seconds * sampleRate * channels * (bits / 8)
        val bytes = ByteArray(44 + dataSize)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun ascii(offset: Int, value: String) {
            for (i in value.indices) bytes[offset + i] = value[i].code.toByte()
        }
        ascii(0, "RIFF")
        buffer.putInt(4, 36 + dataSize)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        buffer.putInt(16, 16)
        buffer.putShort(20, 1)
        buffer.putShort(22, channels.toShort())
        buffer.putInt(24, sampleRate)
        buffer.putInt(28, sampleRate * channels * (bits / 8))
        buffer.putShort(32, (channels * (bits / 8)).toShort())
        buffer.putShort(34, bits.toShort())
        ascii(36, "data")
        buffer.putInt(40, dataSize)
        return bytes
    }
}
