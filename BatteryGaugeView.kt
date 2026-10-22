package com.example.battery_float

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/**
 * Circular battery gauge: a progress ring, the percentage and a lightning bolt while charging.
 *
 * The same view is the floating bubble (with [showDisc]) and the large gauge on the app screen.
 * It only redraws when the shown values actually change, never on a timer, and onDraw does not
 * allocate. Overlay windows are drawn in software by default, so no GPU context is kept alive
 * for the bubble.
 */
class BatteryGaugeView(context: Context) : View(context) {

    /** Draws a dark disc behind the ring (used by the floating bubble). */
    var showDisc: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var percent = -1
    private var charging = false
    private var full = false
    private var label = NO_LEVEL_LABEL

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.OVERLAY_DISC
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Palette.TRACK
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.TEXT_SECONDARY
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ringRect = RectF()
    private val boltPath = Path()

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = describe()
    }

    /** Updates what the gauge shows. Does nothing (and does not redraw) if nothing changed. */
    fun setStatus(percent: Int, charging: Boolean, full: Boolean) {
        if (percent == this.percent && charging == this.charging && full == this.full) return
        this.percent = percent
        this.charging = charging
        this.full = full
        label = if (percent >= 0) percent.toString() else NO_LEVEL_LABEL
        contentDescription = describe()
        invalidate()
    }

    private fun describe(): String = when {
        percent < 0 -> context.getString(R.string.a11y_battery_unknown)
        charging -> context.getString(R.string.a11y_battery_charging, percent)
        else -> context.getString(R.string.a11y_battery, percent)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h)
        if (s <= 0f) return
        val cx = w / 2f
        val cy = h / 2f

        if (showDisc) canvas.drawCircle(cx, cy, s / 2f, discPaint)

        // Ring
        val stroke = s * RING_WIDTH
        val radius = s / 2f - stroke / 2f - s * RING_INSET
        ringRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        trackPaint.strokeWidth = stroke
        canvas.drawOval(ringRect, trackPaint)

        val color = Palette.forLevel(percent, full)
        if (percent > 0) {
            arcPaint.strokeWidth = stroke
            arcPaint.color = color
            canvas.drawArc(ringRect, -90f, 360f * percent / 100f, false, arcPaint)
        }

        // Number and unit
        val numberSize = s * (if (label.length >= 3) NUMBER_SIZE_3_DIGITS else NUMBER_SIZE)
        val digitHeight = numberSize * DIGIT_HEIGHT
        numberPaint.textSize = numberSize
        unitPaint.textSize = numberSize * UNIT_SCALE
        val showUnit = percent >= 0
        val numberWidth = numberPaint.measureText(label)
        val gap = if (showUnit) numberSize * 0.06f else 0f
        val unitWidth = if (showUnit) unitPaint.measureText(UNIT) else 0f
        val textWidth = numberWidth + gap + unitWidth

        val boltHeight = if (charging) s * BOLT_HEIGHT else 0f
        val boltGap = if (charging) s * BOLT_GAP else 0f
        val stackHeight = boltHeight + boltGap + digitHeight
        val top = cy - stackHeight / 2f
        val baseline = top + boltHeight + boltGap + digitHeight

        val textX = cx - textWidth / 2f
        canvas.drawText(label, textX, baseline, numberPaint)
        if (showUnit) canvas.drawText(UNIT, textX + numberWidth + gap, baseline, unitPaint)

        // Lightning bolt above the number while charging
        if (charging) {
            val boltWidth = boltHeight * BOLT_ASPECT
            val left = cx - boltWidth / 2f
            boltPath.rewind()
            for (i in BOLT_POINTS.indices step 2) {
                val px = left + BOLT_POINTS[i] * boltWidth
                val py = top + BOLT_POINTS[i + 1] * boltHeight
                if (i == 0) boltPath.moveTo(px, py) else boltPath.lineTo(px, py)
            }
            boltPath.close()
            boltPaint.color = color
            canvas.drawPath(boltPath, boltPaint)
        }
    }

    private companion object {
        const val NO_LEVEL_LABEL = "--"
        const val UNIT = "%"

        const val RING_WIDTH = 0.085f
        const val RING_INSET = 0.045f
        const val NUMBER_SIZE = 0.31f
        const val NUMBER_SIZE_3_DIGITS = 0.25f
        const val UNIT_SCALE = 0.52f
        const val DIGIT_HEIGHT = 0.72f
        const val BOLT_HEIGHT = 0.20f
        const val BOLT_GAP = 0.04f
        const val BOLT_ASPECT = 0.62f

        /** Lightning bolt outline as x,y pairs in a unit box. */
        val BOLT_POINTS = floatArrayOf(
            0.60f, 0.00f,
            0.10f, 0.55f,
            0.45f, 0.55f,
            0.32f, 1.00f,
            0.90f, 0.40f,
            0.55f, 0.40f,
        )
    }
}
