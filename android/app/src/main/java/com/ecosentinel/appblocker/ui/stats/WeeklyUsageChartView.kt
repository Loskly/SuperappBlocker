package com.ecosentinel.appblocker.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.tracker.UsageTracker
import kotlin.math.max

class WeeklyUsageChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_primary)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface_variant)
        textSize = resources.displayMetrics.density * 11f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.neon_secondary)
        textSize = resources.displayMetrics.density * 10f
        textAlign = Paint.Align.CENTER
    }

    private var entries: List<ChartBarEntry> = emptyList()

    fun setData(entries: List<ChartBarEntry>) {
        this.entries = entries
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        val paddingBottom = labelPaint.textSize * 2.2f
        val paddingTop = valuePaint.textSize * 2f
        val chartHeight = height - paddingBottom - paddingTop
        val barWidth = width.toFloat() / (entries.size * 2f)
        val maxMillis = max(entries.maxOf { it.millis }.toFloat(), 1f)

        entries.forEachIndexed { index, entry ->
            val centerX = barWidth + index * (barWidth * 2f)
            val barHeight = (entry.millis / maxMillis) * chartHeight
            val left = centerX - barWidth * 0.7f
            val top = paddingTop + (chartHeight - barHeight)
            val right = centerX + barWidth * 0.7f
            val bottom = paddingTop + chartHeight
            canvas.drawRoundRect(RectF(left, top, right, bottom), 8f, 8f, barPaint)
            canvas.drawText(entry.label, centerX, height - labelPaint.textSize * 0.4f, labelPaint)
            if (entry.millis > 0L) {
                canvas.drawText(
                    UsageTracker.formatDurationStatic(entry.millis),
                    centerX,
                    top - 6f,
                    valuePaint
                )
            }
        }
    }
}

data class ChartBarEntry(val label: String, val millis: Long)
