package com.example.mbdictate

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mbdictate.storage.ConfigStore
import kotlin.math.abs
import kotlin.math.sin

class OverlayController(
    private val service: AccessibilityService,
) : DictationController.Listener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = service.getSharedPreferences("mbdictate_overlay", Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private var root: FrameLayout? = null
    private var collapsed: FrameLayout? = null
    private var collapsedIcon: ImageView? = null
    private var expanded: LinearLayout? = null
    private var wave: WaveView? = null
    private var status: TextView? = null
    private var sendButton: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var attached = false
    private var currentState = DictationController.State.IDLE

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var holdStarted = false

    private val holdRunnable = Runnable {
        if (!moved && DictationController.state == DictationController.State.IDLE) {
            holdStarted = true
            DictationController.dismissError()
            DictationController.startHold()
            setCollapsed(recording = true)
        }
    }

    fun attachListener() {
        DictationController.listener = this
    }

    fun showBubble() {
        if (root == null) buildViews()
        if (!attached) {
            val view = root ?: return
            val layoutParams = params ?: return
            clampPosition(layoutParams)
            try {
                windowManager.addView(view, layoutParams)
                attached = true
            } catch (_: Exception) {
                return
            }
        }
        if (DictationController.state == DictationController.State.IDLE) {
            setCollapsed(recording = false)
        }
    }

    fun hideBubble() {
        if (DictationController.isBusy()) return
        if (!attached) return
        try {
            windowManager.removeView(root)
        } catch (_: Exception) {
        }
        attached = false
    }

    fun destroy() {
        mainHandler.removeCallbacks(holdRunnable)
        if (DictationController.listener === this) {
            DictationController.listener = null
        }
        if (attached) {
            try {
                windowManager.removeView(root)
            } catch (_: Exception) {
            }
            attached = false
        }
        root = null
        collapsed = null
        expanded = null
        wave = null
        status = null
    }

    private fun buildViews() {
        val rootView = FrameLayout(service)
        rootView.setPadding(dp(4), dp(4), dp(4), dp(4))
        collapsed = buildCollapsed()
        expanded = buildExpanded()
        rootView.addView(collapsed)
        rootView.addView(expanded)
        root = rootView

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = prefs.getInt("x", dp(16))
        layoutParams.y = prefs.getInt("y", dp(220))
        params = layoutParams
        setCollapsed(recording = false)
    }

    private fun buildCollapsed(): FrameLayout {
        val container = FrameLayout(service)
        val size = dp(64)
        container.layoutParams = FrameLayout.LayoutParams(size, size)
        val background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#4F46E5"))
            setStroke(dp(2), Color.parseColor("#A5B4FC"))
        }
        container.background = background
        container.elevation = dp(8).toFloat()

        val icon = ImageView(service)
        icon.setImageResource(android.R.drawable.ic_btn_speak_now)
        icon.setColorFilter(Color.WHITE)
        icon.contentDescription = "Dictate"
        val iconSize = dp(34)
        container.addView(
            icon,
            FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER),
        )
        collapsedIcon = icon
        container.setOnTouchListener(collapsedTouchListener())
        return container
    }

    private fun buildExpanded(): LinearLayout {
        val container = LinearLayout(service)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(10), dp(8), dp(10), dp(8))
        container.background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(Color.parseColor("#EE111827"))
            setStroke(dp(1), Color.parseColor("#6B7280"))
        }
        container.elevation = dp(10).toFloat()

        val row = LinearLayout(service)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val cancel = actionText("✕", "Cancel dictation")
        cancel.setOnClickListener {
            DictationController.cancel()
            setCollapsed(recording = false)
        }
        row.addView(cancel)

        val waveView = WaveView(service)
        wave = waveView
        row.addView(
            waveView,
            LinearLayout.LayoutParams(dp(78), dp(36)),
        )

        val send = actionText("➤", "Stop and insert")
        sendButton = send
        send.setOnClickListener {
            if (DictationController.state == DictationController.State.LISTENING_CONTINUOUS) {
                DictationController.stopAndTranscribe()
            }
        }
        row.addView(send)

        container.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val statusView = TextView(service).apply {
            setTextColor(Color.parseColor("#E5E7EB"))
            textSize = 12f
            maxLines = 2
            setPadding(dp(6), dp(2), dp(6), 0)
        }
        status = statusView
        container.addView(statusView)

        container.visibility = View.GONE
        return container
    }

    private fun actionText(label: String, description: String): TextView {
        return TextView(service).apply {
            text = label
            contentDescription = description
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
    }

    private fun collapsedTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params?.x ?: 0
                    startY = params?.y ?: 0
                    moved = false
                    holdStarted = false
                    mainHandler.removeCallbacks(holdRunnable)
                    mainHandler.postDelayed(holdRunnable, 220)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!holdStarted && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        moved = true
                        mainHandler.removeCallbacks(holdRunnable)
                        val layoutParams = params ?: return@OnTouchListener false
                        layoutParams.x = startX + dx.toInt()
                        layoutParams.y = startY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(root, layoutParams)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(holdRunnable)
                    if (holdStarted) {
                        DictationController.stopAndTranscribe()
                    } else if (moved) {
                        snapToEdge()
                    } else {
                        DictationController.dismissError()
                        DictationController.startContinuous()
                        setExpanded("Listening…")
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(holdRunnable)
                    if (holdStarted) {
                        DictationController.cancel()
                    } else if (moved) {
                        snapToEdge()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun setCollapsed(recording: Boolean) {
        collapsed?.visibility = View.VISIBLE
        expanded?.visibility = View.GONE
        val background = collapsed?.background as? GradientDrawable
        background?.setColor(
            Color.parseColor(if (recording) "#DC2626" else "#4F46E5"),
        )
        collapsedIcon?.alpha = if (recording) 0.7f else 1f
    }

    private fun setExpanded(message: String?) {
        collapsed?.visibility = View.GONE
        expanded?.visibility = View.VISIBLE
        if (message != null) status?.text = message
        sendButton?.alpha = if (
            DictationController.state == DictationController.State.LISTENING_CONTINUOUS
        ) {
            1f
        } else {
            0.45f
        }
    }

    private fun snapToEdge() {
        val layoutParams = params ?: return
        val width = service.resources.displayMetrics.widthPixels
        val viewWidth = root?.width ?: dp(64)
        layoutParams.x = if (layoutParams.x + viewWidth / 2 < width / 2) {
            dp(4)
        } else {
            width - viewWidth - dp(4)
        }
        layoutParams.y = layoutParams.y.coerceIn(0, service.resources.displayMetrics.heightPixels - viewWidth)
        try {
            windowManager.updateViewLayout(root, layoutParams)
        } catch (_: Exception) {
        }
        prefs.edit()
            .putInt("x", layoutParams.x)
            .putInt("y", layoutParams.y)
            .apply()
    }

    private fun clampPosition(layoutParams: WindowManager.LayoutParams) {
        val metrics = service.resources.displayMetrics
        layoutParams.x = layoutParams.x.coerceIn(0, metrics.widthPixels - dp(64))
        layoutParams.y = layoutParams.y.coerceIn(0, metrics.heightPixels - dp(64))
    }

    override fun onState(state: DictationController.State) {
        currentState = state
        mainHandler.post {
            when (state) {
                DictationController.State.IDLE -> setCollapsed(recording = false)
                DictationController.State.LISTENING_HOLD -> setCollapsed(recording = true)
                DictationController.State.LISTENING_CONTINUOUS ->
                    setExpanded("Listening… tap send to finish")
                DictationController.State.TRANSCRIBING ->
                    setExpanded("Transcribing…")
                DictationController.State.INSERTING ->
                    setExpanded("Inserting…")
                DictationController.State.ERROR -> setExpanded(status?.text?.toString())
            }
        }
    }

    override fun onAmplitude(level: Float) {
        wave?.setAmplitude(level)
    }

    override fun onMessage(message: String?) {
        mainHandler.post {
            if (message != null) {
                status?.text = message
                if (expanded?.visibility != View.VISIBLE) {
                    status?.postDelayed({
                        if (currentState == DictationController.State.IDLE || currentState == DictationController.State.ERROR) {
                            status?.text = null
                        }
                    }, 2500)
                }
            } else {
                status?.text = null
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            service.resources.displayMetrics,
        ).toInt()
    }

    class WaveView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A5B4FC")
            strokeWidth = dp(context, 3f)
            strokeCap = Paint.Cap.ROUND
        }
        private var level = 0f

        fun setAmplitude(value: Float) {
            level = value.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bars = 7
            val gap = width.toFloat() / (bars + 1)
            val centerY = height / 2f
            val time = System.currentTimeMillis() / 180.0
            for (i in 0 until bars) {
                val phase = sin(time + i * 0.7).toFloat() * 0.35f + 0.65f
                val minHeight = height * 0.18f
                val maxHeight = height * 0.95f
                val heightPx = minHeight + (maxHeight - minHeight) *
                    (0.25f + level * 0.75f) * (0.55f + 0.45f * sin(
                    (i * 0.9f) + time.toFloat(),
                ).let { (it + 1f) / 2f })
                val x = gap * (i + 1)
                canvas.drawLine(
                    x,
                    centerY - heightPx / 2f,
                    x,
                    centerY + heightPx / 2f,
                    paint,
                )
            }
            if (level > 0f) postInvalidateOnAnimation()
        }

        private fun dp(context: Context, value: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.resources.displayMetrics,
            )
        }
    }
}
