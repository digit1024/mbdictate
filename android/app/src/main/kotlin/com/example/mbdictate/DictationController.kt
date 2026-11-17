package com.example.mbdictate

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.mbdictate.insert.TextInserter
import com.example.mbdictate.recording.AudioRecorder
import com.example.mbdictate.storage.ConfigStore
import com.example.mbdictate.storage.DictateConfig
import com.example.mbdictate.storage.SecureKeyStore
import com.example.mbdictate.stt.SttClient
import kotlin.concurrent.thread

object DictationController {
    private const val TAG = "DictationController"

    enum class State {
        IDLE,
        LISTENING_HOLD,
        LISTENING_CONTINUOUS,
        TRANSCRIBING,
        INSERTING,
        ERROR,
    }

    interface Listener {
        fun onState(state: State)
        fun onAmplitude(level: Float)
        fun onMessage(message: String?)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var service: AccessibilityService? = null
    private var appContext: Context? = null
    private var recorder: AudioRecorder? = null
    private var timer: Runnable? = null
    private var config: DictateConfig? = null
    private var startedAt = 0L

    var listener: Listener? = null
    var state: State = State.IDLE
        private set

    fun attach(service: AccessibilityService) {
        this.service = service
        this.appContext = service.applicationContext
    }

    fun detach() {
        cancel()
        service = null
        appContext = null
    }

    fun startHold() {
        startRecording(continuous = false)
    }

    fun startContinuous() {
        startRecording(continuous = true)
    }

    private fun startRecording(continuous: Boolean) {
        val context = appContext ?: run {
            fail("Dictation service is not available")
            return
        }
        if (state == State.LISTENING_HOLD || state == State.LISTENING_CONTINUOUS ||
            state == State.TRANSCRIBING
        ) {
            return
        }

        val loaded = ConfigStore.load(context)
        if (loaded.stt.endpoint.isBlank() || loaded.stt.model.isBlank()) {
            fail("Configure an STT endpoint and model in Settings")
            return
        }
        val apiKey = SecureKeyStore.get(context, loaded.providerId)
        if (apiKey.isNullOrBlank()) {
            fail("Add an API key for ${loaded.providerLabel} in Settings")
            return
        }
        config = loaded

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            fail("Microphone permission is not granted")
            return
        }

        val recorder = AudioRecorder(context) { level ->
            listener?.onAmplitude(level)
        }
        if (!recorder.start()) {
            fail("Could not start recording")
            return
        }
        this.recorder = recorder
        startedAt = System.currentTimeMillis()

        setState(if (continuous) State.LISTENING_CONTINUOUS else State.LISTENING_HOLD)
        listener?.onMessage(if (continuous) "Listening…" else "Listening… release to send")
        scheduleLimit()
    }

    private fun scheduleLimit() {
        timer?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (state == State.LISTENING_HOLD || state == State.LISTENING_CONTINUOUS) {
                stopAndTranscribe()
            }
        }
        timer = runnable
        mainHandler.postDelayed(runnable, 180_000)
    }

    fun stopAndTranscribe() {
        if (state != State.LISTENING_HOLD && state != State.LISTENING_CONTINUOUS) {
            return
        }
        timer?.let { mainHandler.removeCallbacks(it) }
        timer = null
        val activeRecorder = recorder ?: run {
            fail("Recorder is not available")
            return
        }
        recorder = null
        val elapsed = System.currentTimeMillis() - startedAt
        val wav = activeRecorder.stop()
        listener?.onAmplitude(0f)
        if (elapsed < 400 || wav == null || wav.size <= 44) {
            setState(State.IDLE)
            listener?.onMessage("Nothing recorded")
            return
        }

        val loaded = config ?: ConfigStore.load(appContext!!)
        setState(State.TRANSCRIBING)
        listener?.onMessage("Transcribing…")
        val existingService = service
        val context = appContext

        thread(name = "mbdictate-stt", start = true) {
            try {
                val apiKey = context?.let { SecureKeyStore.get(it, loaded.providerId) }
                val text = SttClient.transcribe(loaded, wav, apiKey)
                mainHandler.post {
                    insertResult(existingService, context, text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                mainHandler.post {
                    fail(e.message ?: "Transcription failed")
                }
            }
        }
    }

    private fun insertResult(
        accessibilityService: AccessibilityService?,
        context: Context?,
        text: String,
    ) {
        if (state != State.TRANSCRIBING) return
        if (accessibilityService == null) {
            copyToClipboard(context, text)
            fail("Copied to clipboard. Accessibility service is not connected.")
            return
        }
        setState(State.INSERTING)
        listener?.onMessage("Inserting…")
        val result = TextInserter.insert(accessibilityService, text)
        if (result.success) {
            setState(State.IDLE)
            listener?.onMessage(null)
        } else {
            if (result.copied) copyToClipboard(context, text)
            fail(result.message ?: "Could not insert text")
        }
    }

    fun cancel() {
        timer?.let { mainHandler.removeCallbacks(it) }
        timer = null
        recorder?.cancel()
        recorder = null
        if (state != State.IDLE) {
            setState(State.IDLE)
            listener?.onMessage(null)
        }
    }

    fun dismissError() {
        if (state == State.ERROR) {
            setState(State.IDLE)
            listener?.onMessage(null)
        }
    }

    private fun copyToClipboard(context: Context?, text: String) {
        if (context == null) return
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("mbdictate", text))
    }

    private fun fail(message: String) {
        setState(State.ERROR)
        listener?.onMessage(message)
        Log.w(TAG, message)
    }

    private fun setState(next: State) {
        state = next
        listener?.onState(next)
    }

    fun isBusy(): Boolean =
        state == State.LISTENING_HOLD || state == State.LISTENING_CONTINUOUS ||
            state == State.TRANSCRIBING || state == State.INSERTING
}
