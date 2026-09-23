package com.ecosentinel.appblocker.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.ecosentinel.appblocker.R

class MainBottomNavigationBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    data class Tab(
        @IdRes val itemId: Int,
        val iconRes: Int,
        val labelRes: Int
    )

    private val tabs = listOf(
        Tab(R.id.nav_home, R.drawable.ic_nav_home, R.string.nav_home),
        Tab(R.id.nav_settings, android.R.drawable.ic_menu_preferences, R.string.nav_settings),
        Tab(R.id.nav_limits, R.drawable.ic_nav_limits, R.string.nav_limits),
        Tab(R.id.nav_stats, R.drawable.ic_nav_stats, R.string.nav_stats),
        Tab(R.id.nav_todo, R.drawable.ic_nav_todo, R.string.nav_todo),
        Tab(R.id.nav_calories, R.drawable.ic_nav_calories, R.string.nav_calories),
        Tab(R.id.nav_features, R.drawable.ic_nav_features, R.string.nav_features)
    )

    private val tabViews = LinkedHashMap<Int, TabView>()
    private var selectedItemId: Int = R.id.nav_home
    private var onItemSelectedListener: ((Int) -> Boolean)? = null

    private val colorSelected by lazy { ContextCompat.getColor(context, R.color.neon_primary) }
    private val colorUnselected by lazy { ContextCompat.getColor(context, R.color.on_surface_variant) }

    init {
        orientation = HORIZONTAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
        elevation = 8f * resources.displayMetrics.density
        tabs.forEach { tab -> addTab(tab) }
        selectItem(selectedItemId, notify = false)
    }

    fun setOnItemSelectedListener(listener: (Int) -> Boolean) {
        onItemSelectedListener = listener
    }

    fun selectedItemId(): Int = selectedItemId

    fun selectItem(@IdRes itemId: Int, notify: Boolean = true) {
        if (notify) {
            val handled = onItemSelectedListener?.invoke(itemId) ?: false
            if (!handled) {
                return
            }
        }
        selectedItemId = itemId
        tabViews.forEach { (id, view) ->
            val selected = id == itemId
            val color = if (selected) colorSelected else colorUnselected
            view.label.setTextColor(color)
            ImageViewCompat.setImageTintList(view.icon, ColorStateList.valueOf(color))
        }
    }

    private fun addTab(tab: Tab) {
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.item_main_bottom_nav, this, false)
        val holder = TabView(
            root = itemView,
            icon = itemView.findViewById(R.id.navIcon),
            label = itemView.findViewById(R.id.navLabel)
        )
        holder.icon.setImageResource(tab.iconRes)
        holder.label.setText(tab.labelRes)
        holder.root.setOnClickListener {
            if (tab.itemId != selectedItemId) {
                selectItem(tab.itemId, notify = true)
            }
        }
        tabViews[tab.itemId] = holder
        addView(holder.root)
    }

    private data class TabView(
        val root: android.view.View,
        val icon: ImageView,
        val label: TextView
    )
}
