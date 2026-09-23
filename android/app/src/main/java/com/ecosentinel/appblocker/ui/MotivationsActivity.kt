package com.ecosentinel.appblocker.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.MotivationEntity
import com.ecosentinel.appblocker.data.entity.ReportEntity
import com.ecosentinel.appblocker.data.repository.MotivationRepository
import com.ecosentinel.appblocker.data.repository.ReportSettings
import com.ecosentinel.appblocker.databinding.ActivityMotivationsBinding
import com.ecosentinel.appblocker.databinding.ItemMotivationBinding
import com.ecosentinel.appblocker.databinding.ItemReportBinding
import com.ecosentinel.appblocker.service.ReportScheduler
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class MotivationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMotivationsBinding
    private lateinit var motivationRepo: MotivationRepository
    private lateinit var motivationAdapter: MotivationAdapter
    private lateinit var reportAdapter: ReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMotivationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getInstance(this)
        motivationRepo = MotivationRepository(db.motivationDao())

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupLists(db)
        setupSettings()
        
        // Ensure defaults are loaded
        lifecycleScope.launch {
            motivationRepo.getRandomMotivation()
        }
    }

    private fun setupLists(db: AppDatabase) {
        motivationAdapter = MotivationAdapter { entity ->
            lifecycleScope.launch {
                db.motivationDao().delete(entity)
            }
        }
        binding.motivationsRecyclerView.adapter = motivationAdapter
        binding.motivationsRecyclerView.layoutManager = LinearLayoutManager(this)

        reportAdapter = ReportAdapter { entity ->
            AlertDialog.Builder(this)
                .setTitle("Удалить отчёт?")
                .setMessage("Вы уверены, что хотите удалить этот отчёт из истории?")
                .setPositiveButton("Удалить") { _, _ ->
                    lifecycleScope.launch {
                        db.reportDao().deleteById(entity.id)
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
        binding.reportsRecyclerView.adapter = reportAdapter
        binding.reportsRecyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            db.motivationDao().getAll().collectLatest {
                motivationAdapter.submitList(it)
            }
        }

        lifecycleScope.launch {
            db.reportDao().getAll().collectLatest {
                reportAdapter.submitList(it)
            }
        }

        binding.btnAddMotivation.setOnClickListener {
            val input = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("Новая мотивация")
                .setView(input)
                .setPositiveButton("Добавить") { _, _ ->
                    val text = input.text.toString().trim()
                    if (text.isNotEmpty()) {
                        lifecycleScope.launch {
                            motivationRepo.addCustomMotivation(text)
                        }
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun setupSettings() {
        binding.switchReportsEnabled.isChecked = ReportSettings.isEnabled(this)
        binding.editIntervalHours.setText(ReportSettings.getIntervalHours(this).toString())
        binding.editCharCount.setText(ReportSettings.getCharCount(this).toString())
        binding.editMaxSnoozes.setText(ReportSettings.getMaxSnoozes(this).toString())
        binding.editSnoozeDuration.setText(ReportSettings.getSnoozeDurationMins(this).toString())

        val scheduleType = ReportSettings.getScheduleType(this)
        if (scheduleType == 1) {
            binding.radioExactTime.isChecked = true
            binding.containerInterval.visibility = View.GONE
            binding.containerExactTimes.visibility = View.VISIBLE
        } else {
            binding.radioInterval.isChecked = true
            binding.containerInterval.visibility = View.VISIBLE
            binding.containerExactTimes.visibility = View.GONE
        }

        renderExactTimesChips()

        binding.radioGroupScheduleType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.radioExactTime.id) {
                binding.containerInterval.visibility = View.GONE
                binding.containerExactTimes.visibility = View.VISIBLE
            } else {
                binding.containerInterval.visibility = View.VISIBLE
                binding.containerExactTimes.visibility = View.GONE
            }
        }

        binding.btnAddExactTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val timeStr = String.format("%02d:%02d", hourOfDay, minute)
                    ReportSettings.addExactTime(this, timeStr)
                    renderExactTimesChips()
                    if (binding.switchReportsEnabled.isChecked) {
                        ReportScheduler.scheduleNext(this)
                    }
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        }

        binding.switchReportsEnabled.setOnCheckedChangeListener { _, isChecked ->
            ReportSettings.setEnabled(this, isChecked)
        }

        binding.btnSaveSettings.setOnClickListener {
            val interval = binding.editIntervalHours.text.toString().toIntOrNull() ?: 4
            val chars = binding.editCharCount.text.toString().toIntOrNull() ?: 150
            val snoozes = binding.editMaxSnoozes.text.toString().toIntOrNull() ?: 3
            val snoozeDuration = binding.editSnoozeDuration.text.toString().toIntOrNull() ?: 15
            val type = if (binding.radioExactTime.isChecked) 1 else 0

            ReportSettings.setIntervalHours(this, interval)
            ReportSettings.setCharCount(this, chars)
            ReportSettings.setMaxSnoozes(this, snoozes)
            ReportSettings.setSnoozeDurationMins(this, snoozeDuration)
            ReportSettings.setScheduleType(this, type)

            if (binding.switchReportsEnabled.isChecked) {
                ReportScheduler.scheduleNext(this)
            } else {
                ReportScheduler.cancel(this)
            }
            
            AlertDialog.Builder(this)
                .setMessage("Настройки сохранены!")
                .setPositiveButton("ОК", null)
                .show()
        }
    }

    private fun renderExactTimesChips() {
        binding.chipGroupExactTimes.removeAllViews()
        val times = ReportSettings.getExactTimes(this).sorted()
        for (time in times) {
            val chip = Chip(this).apply {
                text = time
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    ReportSettings.removeExactTime(this@MotivationsActivity, time)
                    renderExactTimesChips()
                    if (binding.switchReportsEnabled.isChecked) {
                        ReportScheduler.scheduleNext(this@MotivationsActivity)
                    }
                }
            }
            binding.chipGroupExactTimes.addView(chip)
        }
    }
}

private class MotivationAdapter(
    private val onDelete: (MotivationEntity) -> Unit
) : ListAdapter<MotivationEntity, MotivationAdapter.ViewHolder>(object : DiffUtil.ItemCallback<MotivationEntity>() {
    override fun areItemsTheSame(oldItem: MotivationEntity, newItem: MotivationEntity) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: MotivationEntity, newItem: MotivationEntity) = oldItem == newItem
}) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMotivationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.motivationText.text = item.text
        holder.binding.btnDelete.setOnClickListener { onDelete(item) }
    }

    class ViewHolder(val binding: ItemMotivationBinding) : RecyclerView.ViewHolder(binding.root)
}

private class ReportAdapter(
    private val onDelete: (ReportEntity) -> Unit
) : ListAdapter<ReportEntity, ReportAdapter.VH>(object : DiffUtil.ItemCallback<ReportEntity>() {
    override fun areItemsTheSame(oldItem: ReportEntity, newItem: ReportEntity) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: ReportEntity, newItem: ReportEntity) = oldItem == newItem
}) {
    class VH(val binding: ItemReportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.textReportDate.text = DateFormat.format("dd MMM yyyy, HH:mm", Date(item.timestamp))
        holder.binding.textReportContent.text = item.text
        holder.binding.btnDeleteReport.setOnClickListener {
            onDelete(item)
        }
    }
}
