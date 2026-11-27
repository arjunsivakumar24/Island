package com.islandx.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/** Small animated equaliser bars, like the right side of the iPhone pill. */
class WaveView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heights = FloatArray(6) { 0.3f }
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            for (i in heights.indices) heights[i] = 0.2f + Random.nextFloat() * 0.8f
            invalidate()
            if (running) handler.postDelayed(this, 110)
        }
    }

    fun setColor(c: Int) { paint.color = c; invalidate() }

    fun setActive(on: Boolean) {
        if (on == running) return
        running = on
        if (on) handler.post(tick) else {
            handler.removeCallbacks(tick)
            for (i in heights.indices) heights[i] = 0.25f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val n = heights.size
        val gap = width * 0.12f
        val barW = (width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val h = height * heights[i]
            val left = i * (barW + gap)
            val top = (height - h) / 2f
            canvas.drawRoundRect(RectF(left, top, left + barW, top + h), barW / 2, barW / 2, paint)
        }
    }
}

/**
 * The pill itself. Compact = the thin bar around the camera hole.
 * Expanded = taller card with title, subtitle and optional media controls.
 */
class IslandView(ctx: Context) : LinearLayout(ctx) {

    data class Ui(
        val left: String = "",
        val leftColor: Int = Color.WHITE,
        val right: String = "",
        val rightColor: Int = Color.WHITE,
        val wave: Boolean = false,
        val waveColor: Int = Color.rgb(120, 255, 120),
        val title: String = "",
        val subtitle: String = "",
        val controls: Boolean = false,
        val playing: Boolean = false,
        val expandable: Boolean = false
    )

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null

    var compactWidthDp = 140
    var expandedWidthDp = 340

    private val bg = GradientDrawable().apply { setColor(Color.BLACK) }
    private val topRow = LinearLayout(ctx)
    private val leftText = TextView(ctx)
    private val gap = View(ctx)
    private val rightText = TextView(ctx)
    private val wave = WaveView(ctx)
    private val title = TextView(ctx)
    private val subtitle = TextView(ctx)
    private val controlsRow = LinearLayout(ctx)
    private val playBtn: TextView

    private var expanded = false
    private var current = Ui()
    private var animator: ValueAnimator? = null
    private var curW = 0f
    private var curH = 0f

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val compactH get() = dp(36)
    private val expandedH get() = if (current.controls) dp(150) else dp(86)

    init {
        orientation = VERTICAL
        background = bg
        elevation = dp(6).toFloat()

        topRow.orientation = HORIZONTAL
        topRow.gravity = Gravity.CENTER_VERTICAL
        topRow.setPadding(dp(14), 0, dp(14), 0)

        leftText.textSize = 12f
        leftText.setTypeface(leftText.typeface, android.graphics.Typeface.BOLD)
        rightText.textSize = 12f
        rightText.setTypeface(rightText.typeface, android.graphics.Typeface.BOLD)

        topRow.addView(leftText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        topRow.addView(gap, LayoutParams(0, 1, 1f))   // space for the camera hole
        topRow.addView(rightText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        topRow.addView(wave, LayoutParams(dp(22), dp(14)))
        addView(topRow, LayoutParams(LayoutParams.MATCH_PARENT, compactH))

        title.setTextColor(Color.WHITE)
        title.textSize = 15f
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.setPadding(dp(20), dp(4), dp(20), 0)
        subtitle.setTextColor(Color.argb(170, 255, 255, 255))
        subtitle.textSize = 12f
        subtitle.maxLines = 1
        subtitle.ellipsize = android.text.TextUtils.TruncateAt.END
        subtitle.setPadding(dp(20), 0, dp(20), 0)
        addView(title)
        addView(subtitle)

        controlsRow.orientation = HORIZONTAL
        controlsRow.gravity = Gravity.CENTER
        fun btn(label: String, size: Float, action: () -> Unit): TextView =
            TextView(ctx).apply {
                text = label
                textSize = size
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setOnClickListener { action() }
            }
        playBtn = btn("⏸", 28f) { onPlayPause?.invoke() }
        controlsRow.addView(btn("⏮", 24f) { onPrev?.invoke() }, LayoutParams(dp(64), dp(48)))
        controlsRow.addView(playBtn, LayoutParams(dp(64), dp(48)))
        controlsRow.addView(btn("⏭", 24f) { onNext?.invoke() }, LayoutParams(dp(64), dp(48)))
        addView(controlsRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))

        setOnClickListener { if (current.expandable) onTap?.invoke() }
        setOnLongClickListener { onLongPress?.invoke(); true }

        applyContentVisibility(false)
        curW = dp(compactWidthDp).toFloat()
        curH = compactH.toFloat()
        applySize(curW, curH)
    }

    fun isExpanded() = expanded

    fun render(ui: Ui, expand: Boolean) {
        current = ui
        leftText.text = ui.left
        leftText.setTextColor(ui.leftColor)
        rightText.text = ui.right
        rightText.setTextColor(ui.rightColor)
        rightText.visibility = if (ui.right.isEmpty()) GONE else VISIBLE
        wave.visibility = if (ui.wave) VISIBLE else GONE
        wave.setColor(ui.waveColor)
        wave.setActive(ui.wave && (ui.playing || !ui.controls))
        title.text = ui.title
        subtitle.text = ui.subtitle
        subtitle.visibility = if (ui.subtitle.isEmpty()) GONE else VISIBLE
        playBtn.text = if (ui.playing) "⏸" else "▶"
        visibility = VISIBLE
        setExpanded(expand)
    }

    fun hide() {
        animator?.cancel()
        expanded = false
        visibility = GONE
    }

    fun setExpanded(on: Boolean) {
        expanded = on
        applyContentVisibility(on)
        val targetW = dp(if (on) expandedWidthDp else compactWidthDp).toFloat()
        val targetH = (if (on) expandedH else compactH).toFloat()
        animator?.cancel()
        val sw = curW; val sh = curH
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(if (on) 1.15f else 0.8f)
            addUpdateListener {
                val f = it.animatedValue as Float
                curW = sw + (targetW - sw) * f
                curH = sh + (targetH - sh) * f
                applySize(curW, curH)
            }
            start()
        }
    }

    private fun applyContentVisibility(on: Boolean) {
        val showText = on && (current.title.isNotEmpty())
        title.visibility = if (showText) VISIBLE else GONE
        subtitle.visibility = if (showText && current.subtitle.isNotEmpty()) VISIBLE else GONE
        controlsRow.visibility = if (on && current.controls) VISIBLE else GONE
    }

    private fun applySize(w: Float, h: Float) {
        val lp = layoutParams
        if (lp != null) {
            lp.width = w.toInt().coerceAtLeast(dp(60))
            lp.height = h.toInt().coerceAtLeast(dp(20))
            layoutParams = lp
        } else {
            layoutParams = LayoutParams(w.toInt(), h.toInt())
        }
        bg.cornerRadius = minOf(h / 2f, dp(34).toFloat())
    }
}
