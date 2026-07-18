package com.ecosentinel.appblocker.ui

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
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

/** Standard setup when this list lives inside a [android.widget.ScrollView]. */
fun FullHeightRecyclerView.prepareForScrollParent(context: Context) {
    if (layoutManager == null) {
        layoutManager = LinearLayoutManager(context)
    }
    isNestedScrollingEnabled = false
    setHasFixedSize(false)
}

fun <T> ListAdapter<T, *>.submitListRemeasure(
    recyclerView: FullHeightRecyclerView,
    list: List<T>?
) {
    submitList(list) {
        recyclerView.requestLayout()
    }
}
