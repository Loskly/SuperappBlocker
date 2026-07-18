package com.ecosentinel.appblocker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.FragmentFeaturesBinding
import com.ecosentinel.appblocker.databinding.ItemFeatureBinding

class FeaturesFragment : Fragment() {

    private var _binding: FragmentFeaturesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeaturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = FeatureAdapter { feature ->
            when (feature.id) {
                FEATURE_APP_PASSWORD -> {
                    startActivity(Intent(requireContext(), AppPasswordActivity::class.java))
                }
                FEATURE_FOCUS -> {
                    startActivity(Intent(requireContext(), FocusActivity::class.java))
                }
                FEATURE_SUPER_ALARM -> {
                    startActivity(Intent(requireContext(), SuperAlarmActivity::class.java))
                }
                FEATURE_ADULT_FILTER -> {
                    startActivity(Intent(requireContext(), AdultFilterActivity::class.java))
                }
                FEATURE_SHORTS_REELS -> {
                    startActivity(Intent(requireContext(), InAppFeatureBlockActivity::class.java))
                }
            }
        }
        binding.featuresRecyclerView.prepareForScrollParent(requireContext())
        binding.featuresRecyclerView.adapter = adapter
        adapter.submitList(
            listOf(
                FeatureItem(
                    id = FEATURE_ADULT_FILTER,
                    title = getString(R.string.feature_adult_filter_title),
                    description = getString(R.string.feature_adult_filter_desc),
                    iconRes = R.drawable.ic_feature_adult_filter
                ),
                FeatureItem(
                    id = FEATURE_SHORTS_REELS,
                    title = getString(R.string.feature_shorts_reels_title),
                    description = getString(R.string.feature_shorts_reels_desc),
                    iconRes = R.drawable.ic_feature_shorts_reels
                ),
                FeatureItem(
                    id = FEATURE_SUPER_ALARM,
                    title = getString(R.string.feature_super_alarm_title),
                    description = getString(R.string.feature_super_alarm_desc),
                    iconRes = R.drawable.ic_feature_alarm
                ),
                FeatureItem(
                    id = FEATURE_FOCUS,
                    title = getString(R.string.feature_focus_title),
                    description = getString(R.string.feature_focus_desc),
                    iconRes = R.drawable.ic_feature_focus
                ),
                FeatureItem(
                    id = FEATURE_APP_PASSWORD,
                    title = getString(R.string.feature_app_password_title),
                    description = getString(R.string.feature_app_password_desc),
                    iconRes = R.drawable.ic_feature_password
                )
            )
        )
        binding.featuresRecyclerView.requestLayout()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val FEATURE_APP_PASSWORD = "app_password"
        const val FEATURE_FOCUS = "focus"
        const val FEATURE_SUPER_ALARM = "super_alarm"
        const val FEATURE_ADULT_FILTER = "adult_filter"
        const val FEATURE_SHORTS_REELS = "shorts_reels"
    }
}

data class FeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int
)

private class FeatureAdapter(
    private val onClick: (FeatureItem) -> Unit
) : RecyclerView.Adapter<FeatureAdapter.ViewHolder>() {

    private var items: List<FeatureItem> = emptyList()

    fun submitList(list: List<FeatureItem>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemFeatureBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FeatureItem) {
            binding.featureTitle.text = item.title
            binding.featureDescription.text = item.description
            binding.featureIcon.setImageResource(item.iconRes)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeatureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
