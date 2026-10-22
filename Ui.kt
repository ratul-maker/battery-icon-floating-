package com.example.battery_float

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Small helpers that build the app screen in code. Building the views directly (instead of
 * inflating XML and pulling in a UI library) keeps the APK tiny and the screen fast to open.
 */
internal class Ui(private val context: Context) {

    private val density = context.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    fun rounded(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    /** A rounded background that shows a ripple (clipped to the rounded shape) when pressed. */
    fun pressable(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): Drawable =
        RippleDrawable(
            ColorStateList.valueOf(Palette.RIPPLE),
            rounded(color, radiusDp, strokeColor, strokeDp),
            rounded(Palette.WHITE, radiusDp)
        )

    fun label(text: CharSequence, sizeSp: Float, color: Int, medium: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    fun button(text: CharSequence, fillColor: Int, textColor: Int, onClick: () -> Unit): Button =
        Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            minHeight = dp(56)
            minimumHeight = dp(56)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            stateListAnimator = null
            this.background = pressable(fillColor, 16)
            setOnClickListener { onClick() }
        }

    fun card(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.CARD, 24, Palette.CARD_STROKE, 1)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

    /** Layout params with an optional top margin (dp). */
    fun params(width: Int, height: Int, topDp: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height, weight).apply { topMargin = dp(topDp) }

    fun wrapParams(topDp: Int = 0): LinearLayout.LayoutParams =
        params(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, topDp)
}

/** A vertical column that never grows wider than [maxWidth] (keeps the screen tidy on tablets). */
internal class MaxWidthLinearLayout(context: Context, private val maxWidth: Int) :
    LinearLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = View.MeasureSpec.getMode(widthMeasureSpec)
        val size = View.MeasureSpec.getSize(widthMeasureSpec)
        val spec = if (mode != View.MeasureSpec.UNSPECIFIED && size > maxWidth) {
            View.MeasureSpec.makeMeasureSpec(maxWidth, mode)
        } else {
            widthMeasureSpec
        }
        super.onMeasure(spec, heightMeasureSpec)
    }
}
