package com.example.mbdictate.recording

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    fun wrapPcm(
        pcm: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        writeAscii(header, 0, "RIFF")
        buffer.putInt(4, 36 + pcm.size)
        writeAscii(header, 8, "WAVE")
        writeAscii(header, 12, "fmt ")
        buffer.putInt(16, 16)
        buffer.putShort(20, 1)
        buffer.putShort(22, channels.toShort())
        buffer.putInt(24, sampleRate)
        buffer.putInt(28, byteRate)
        buffer.putShort(32, blockAlign.toShort())
        buffer.putShort(34, bitsPerSample.toShort())
        writeAscii(header, 36, "data")
        buffer.putInt(40, pcm.size)

        val out = ByteArray(44 + pcm.size)
        System.arraycopy(header, 0, out, 0, 44)
        System.arraycopy(pcm, 0, out, 44, pcm.size)
        return out
    }

    private fun writeAscii(bytes: ByteArray, offset: Int, value: String) {
        for (i in value.indices) {
            bytes[offset + i] = value[i].code.toByte()
        }
    }
}
