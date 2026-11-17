package com.example.mbdictate

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Splits PCM16 WAV audio into provider-sized chunks and merges transcripts.
 * Pure JVM code so it can be covered by local unit tests.
 */
object WavChunker {
    private data class WavInfo(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val byteRate: Int,
        val blockAlign: Int,
        val bitsPerSample: Int,
        val dataOffset: Int,
        val dataSize: Int,
    )

    fun chunkWav(
        bytes: ByteArray,
        maxSeconds: Int,
        overlapSeconds: Double = 1.0,
    ): List<ByteArray> {
        if (maxSeconds <= 0) return listOf(bytes)
        val info = parse(bytes) ?: return listOf(bytes)
        val frameSize = info.channels * (info.bitsPerSample / 8)
        if (frameSize <= 0 || info.byteRate <= 0) return listOf(bytes)

        val maxBytes = maxSeconds * info.byteRate
        if (info.dataSize <= maxBytes) return listOf(bytes)

        val overlapRaw = (overlapSeconds * info.byteRate).toInt()
        val overlapBytes = (overlapRaw / frameSize) * frameSize
        val chunks = mutableListOf<ByteArray>()
        var start = 0
        while (start < info.dataSize) {
            val end = minOf(start + maxBytes, info.dataSize)
            val alignedLength = ((end - start) / frameSize) * frameSize
            if (alignedLength <= 0) break
            val alignedEnd = start + alignedLength
            val data = bytes.copyOfRange(
                info.dataOffset + start,
                info.dataOffset + alignedEnd,
            )
            chunks.add(buildWav(info, data))
            if (alignedEnd >= info.dataSize) break
            val next = alignedEnd - overlapBytes
            start = if (next > start) next else alignedEnd
        }
        return chunks.ifEmpty { listOf(bytes) }
    }

    fun mergeTranscripts(parts: List<String>): String {
        val pieces = parts
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
        if (pieces.isEmpty()) return ""
        if (pieces.size == 1) return pieces.first()

        val words = pieces.first().split(" ").toMutableList()
        for (index in 1 until pieces.size) {
            val next = pieces[index].split(" ")
            val maxOverlap = minOf(words.size, next.size)
            var overlap = 0
            for (k in maxOverlap downTo 1) {
                val a = words.subList(words.size - k, words.size)
                    .joinToString(" ").lowercase()
                val b = next.subList(0, k).joinToString(" ").lowercase()
                if (a == b) {
                    overlap = k
                    break
                }
            }
            words.addAll(next.subList(overlap, next.size))
        }
        return words.joinToString(" ")
    }

    private fun parse(bytes: ByteArray): WavInfo? {
        if (bytes.size < 44) return null
        if (ascii(bytes, 0, 4) != "RIFF" || ascii(bytes, 8, 4) != "WAVE") {
            return null
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var audioFormat: Int? = null
        var channels: Int? = null
        var sampleRate: Int? = null
        var byteRate: Int? = null
        var blockAlign: Int? = null
        var bitsPerSample: Int? = null
        var dataOffset: Int? = null
        var dataSize: Int? = null

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset, 4)
            val size = buffer.getInt(offset + 4)
            val body = offset + 8
            if (id == "fmt " && body + 16 <= bytes.size) {
                audioFormat = buffer.getShort(body).toInt() and 0xFFFF
                channels = buffer.getShort(body + 2).toInt() and 0xFFFF
                sampleRate = buffer.getInt(body + 4)
                byteRate = buffer.getInt(body + 8)
                blockAlign = buffer.getShort(body + 12).toInt() and 0xFFFF
                bitsPerSample = buffer.getShort(body + 14).toInt() and 0xFFFF
            } else if (id == "data") {
                dataOffset = body
                dataSize = minOf(size, bytes.size - body)
            }
            val padded = if (size % 2 == 0) size else size + 1
            offset = body + padded
        }

        if (audioFormat == null || channels == null || sampleRate == null ||
            byteRate == null || blockAlign == null || bitsPerSample == null ||
            dataOffset == null || dataSize == null || dataSize <= 0
        ) {
            return null
        }
        return WavInfo(
            audioFormat = audioFormat,
            channels = channels,
            sampleRate = sampleRate,
            byteRate = byteRate,
            blockAlign = blockAlign,
            bitsPerSample = bitsPerSample,
            dataOffset = dataOffset,
            dataSize = dataSize,
        )
    }

    private fun buildWav(info: WavInfo, data: ByteArray): ByteArray {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        writeAscii(header, 0, "RIFF")
        buffer.putInt(4, 36 + data.size)
        writeAscii(header, 8, "WAVE")
        writeAscii(header, 12, "fmt ")
        buffer.putInt(16, 16)
        buffer.putShort(20, info.audioFormat.toShort())
        buffer.putShort(22, info.channels.toShort())
        buffer.putInt(24, info.sampleRate)
        buffer.putInt(28, info.byteRate)
        buffer.putShort(32, info.blockAlign.toShort())
        buffer.putShort(34, info.bitsPerSample.toShort())
        writeAscii(header, 36, "data")
        buffer.putInt(40, data.size)

        val out = ByteArray(44 + data.size)
        System.arraycopy(header, 0, out, 0, 44)
        System.arraycopy(data, 0, out, 44, data.size)
        return out
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String {
        return String(bytes, offset, length, Charsets.US_ASCII)
    }

    private fun writeAscii(bytes: ByteArray, offset: Int, value: String) {
        for (i in value.indices) {
            bytes[offset + i] = value[i].code.toByte()
        }
    }
}
