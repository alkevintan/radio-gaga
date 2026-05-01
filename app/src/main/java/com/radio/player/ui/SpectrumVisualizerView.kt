package com.radio.player.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class SpectrumVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 4
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.color = context.getColor(com.radio.player.R.color.spectrum_bar)
    }

    private val barHeights = FloatArray(barCount) { MIN_RATIO }
    private val targetHeights = FloatArray(barCount) { MIN_RATIO }
    private var animating = false
    private var animator: ValueAnimator? = null

    private val barCornerRadius = 3f
    private val circumference = 2f * Math.PI.toFloat()

    companion object {
        private const val MIN_RATIO = 0.15f
        private const val MAX_RATIO = 1.0f
    }

    fun setPlaying(playing: Boolean) {
        if (playing && !animating) {
            startAnimation()
        } else if (!playing) {
            stopAnimation()
        }
    }

    fun setBarColor(color: Int) {
        paint.color = color
        invalidate()
    }

    private fun startAnimation() {
        animating = true
        generateTargets()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                for (i in 0 until barCount) {
                    barHeights[i] += (targetHeights[i] - barHeights[i]) * 0.25f
                }
                val done = barHeights.withIndex().all { (i, h) ->
                    Math.abs(h - targetHeights[i]) < 0.02f
                }
                if (done) {
                    generateTargets()
                }
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animating = false
        animator?.cancel()
        animator = null
        for (i in 0 until barCount) {
            targetHeights[i] = MIN_RATIO
        }
        val settle = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener {
                for (i in 0 until barCount) {
                    barHeights[i] += (MIN_RATIO - barHeights[i]) * 0.15f
                }
                invalidate()
            }
            start()
        }
    }

    private fun generateTargets() {
        for (i in 0 until barCount) {
            targetHeights[i] = MIN_RATIO + (Math.random().toFloat() * (MAX_RATIO - MIN_RATIO))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val totalBarWidth = w / barCount
        val barWidth = totalBarWidth * 0.6f
        val gap = totalBarWidth * 0.4f

        for (i in 0 until barCount) {
            val barH = h * barHeights[i]
            val left = i * totalBarWidth + gap / 2f
            val top = (h - barH) / 2f
            val right = left + barWidth
            val bottom = top + barH

            canvas.drawRoundRect(left, top, right, bottom, barCornerRadius, barCornerRadius, paint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (barCount * 12f * resources.displayMetrics.density).toInt()
        val desiredH = (32f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }
}