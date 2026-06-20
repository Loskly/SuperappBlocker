package com.ecosentinel.appblocker.ui

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * Expands to the full height of all items. Use inside ScrollView instead of a fixed-height list.
 */
class FullHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val expandedHeightSpec = MeasureSpec.makeMeasureSpec(
            Int.MAX_VALUE shr 2,
            MeasureSpec.AT_MOST
        )
        super.onMeasure(widthSpec, expandedHeightSpec)
    }
}
