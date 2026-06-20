package com.ecosentinel.appblocker.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ecosentinel.appblocker.R
import kotlin.math.min

class CategoryUsageChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val sliceColors = intArrayOf(
        R.color.chart_1,
        R.color.chart_2,
        R.color.chart_3,
        R.color.chart_4,
        R.color.chart_5,
        R.color.chart_6
    )
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface)
        textSize = resources.displayMetrics.density * 11f
    }

    private var entries: List<ChartSliceEntry> = emptyList()

    fun setData(entries: List<ChartSliceEntry>) {
        this.entries = entries
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        val total = entries.sumOf { it.millis }.toFloat().coerceAtLeast(1f)
        val size = min(width, height).toFloat()
        val radius = size * 0.32f
        val cx = width / 2f
        val cy = size * 0.38f
        var startAngle = -90f

        entries.forEachIndexed { index, entry ->
            val sweep = (entry.millis / total) * 360f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, sliceColors[index % sliceColors.size])
                style = Paint.Style.FILL
            }
            canvas.drawArc(
                RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                startAngle,
                sweep,
                true,
                paint
            )
            startAngle += sweep
        }

        var legendY = cy + radius + textPaint.textSize * 1.5f
        entries.forEachIndexed { index, entry ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, sliceColors[index % sliceColors.size])
            }
            canvas.drawCircle(24f, legendY - textPaint.textSize * 0.35f, 8f, paint)
            val percent = ((entry.millis / total) * 100).toInt()
            canvas.drawText("${entry.label} · $percent%", 40f, legendY, textPaint)
            legendY += textPaint.textSize * 1.6f
        }
    }
}

data class ChartSliceEntry(val label: String, val millis: Long)
