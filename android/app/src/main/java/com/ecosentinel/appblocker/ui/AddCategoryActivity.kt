package com.ecosentinel.appblocker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ecosentinel.appblocker.databinding.ActivityAddCategoryBinding
import com.ecosentinel.appblocker.tracker.AppCategory

class AddCategoryActivity : AppCompatActivity(), EditRuleDialog.Listener {

    private lateinit var binding: ActivityAddCategoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val adapter = AddCategoryAdapter { category ->
            EditRuleDialog.newInstanceForCategory(category.id, category.displayName)
                .show(supportFragmentManager, "edit_rule")
        }
        binding.categoriesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.categoriesRecyclerView.adapter = adapter
        binding.categoriesRecyclerView.setHasFixedSize(false)
        adapter.submitList(AppCategory.selectableCategories())
    }

    override fun onRuleSaved() {
        setResult(RESULT_OK)
        finish()
    }
}
