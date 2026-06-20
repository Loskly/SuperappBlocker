package com.ecosentinel.appblocker.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.tracker.HourlyUsageBucket
import kotlin.math.max

class HourlyUsageChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_primary)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface_variant)
        textSize = resources.displayMetrics.density * 10f
        textAlign = Paint.Align.CENTER
    }

    private var buckets: List<HourlyUsageBucket> = emptyList()

    fun setData(buckets: List<HourlyUsageBucket>) {
        this.buckets = buckets
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (buckets.isEmpty()) return

        val paddingBottom = axisPaint.textSize * 2f
        val paddingTop = resources.displayMetrics.density * 8f
        val chartHeight = height - paddingBottom - paddingTop
        val barSlotWidth = width.toFloat() / buckets.size
        val maxMillis = max(buckets.maxOf { it.millis }.toFloat(), 1f)

        buckets.forEachIndexed { index, bucket ->
            val barHeight = (bucket.millis / maxMillis) * chartHeight
            val left = index * barSlotWidth + barSlotWidth * 0.15f
            val right = (index + 1) * barSlotWidth - barSlotWidth * 0.15f
            val top = paddingTop + (chartHeight - barHeight)
            val bottom = paddingTop + chartHeight
            canvas.drawRoundRect(RectF(left, top, right, bottom), 4f, 4f, barPaint)

            if (bucket.hour % 6 == 0) {
                val centerX = (left + right) / 2f
                canvas.drawText(
                    String.format("%02d", bucket.hour),
                    centerX,
                    height - axisPaint.textSize * 0.3f,
                    axisPaint
                )
            }
        }
    }
}
