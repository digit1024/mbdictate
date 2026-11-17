package com.example.mbdictate.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioRecorder(
    private val context: Context,
    private val onAmplitude: (Float) -> Unit,
) {
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val MAX_MILLIS = 180_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var recording = false
    private var worker: Thread? = null
    @Volatile private var wavResult: ByteArray? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(): Boolean {
        if (recording) return true
        if (!hasPermission()) return false

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer, 4096)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed", e)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        wavResult = null
        recording = true
        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            recording = false
            record.release()
            return false
        }

        worker = thread(name = "mbdictate-recorder", start = true) {
            val pcm = ByteArrayOutputStream()
            val buffer = ShortArray(2048)
            val startedAt = System.currentTimeMillis()
            var lastAmplitudeAt = 0L
            try {
                while (recording) {
                    if (System.currentTimeMillis() - startedAt >= MAX_MILLIS) {
                        break
                    }
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toInt()
                            pcm.write(sample and 0xFF)
                            pcm.write((sample shr 8) and 0xFF)
                            sum += sample.toDouble() * sample.toDouble()
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastAmplitudeAt > 80) {
                            lastAmplitudeAt = now
                            val rms = sqrt(sum / read) / 32768.0
                            val level = rms.coerceIn(0.0, 1.0).toFloat()
                            mainHandler.post { onAmplitude(level) }
                        }
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {}
                record.release()
                wavResult = WavWriter.wrapPcm(pcm.toByteArray())
                recording = false
                mainHandler.post { onAmplitude(0f) }
            }
        }
        return true
    }

    fun stop(): ByteArray? {
        recording = false
        worker?.join(4000)
        worker = null
        val result = wavResult
        wavResult = null
        return result
    }

    fun cancel() {
        recording = false
        worker?.join(4000)
        worker = null
        wavResult = null
    }

    fun isRecording(): Boolean = recording
}
