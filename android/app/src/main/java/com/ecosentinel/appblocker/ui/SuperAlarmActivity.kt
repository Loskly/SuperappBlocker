package com.ecosentinel.appblocker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.alarm.AlarmPreviewLauncher
import com.ecosentinel.appblocker.alarm.AlarmChallengeDifficulty
import com.ecosentinel.appblocker.alarm.AlarmRepeatDays
import com.ecosentinel.appblocker.alarm.AlarmSoundHelper
import com.ecosentinel.appblocker.alarm.AlarmScheduler
import com.ecosentinel.appblocker.alarm.SuperAlarmManager
import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import com.ecosentinel.appblocker.databinding.ActivitySuperAlarmBinding
import com.ecosentinel.appblocker.databinding.ItemSuperAlarmBinding
import com.ecosentinel.appblocker.util.PermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SuperAlarmActivity : AppCompatActivity(), SuperAlarmEditDialog.Listener {

    private lateinit var binding: ActivitySuperAlarmBinding
    private lateinit var alarmManager: SuperAlarmManager
    private lateinit var adapter: SuperAlarmAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySuperAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmManager = SuperAlarmManager(this)
        adapter = SuperAlarmAdapter(
            onToggle = { alarm, enabled ->
                lifecycleScope.launch {
                    alarmManager.setEnabled(alarm.id, enabled)
                }
            },
            onEdit = { alarm ->
                SuperAlarmEditDialog.newInstance(alarm)
                    .show(supportFragmentManager, "edit_alarm")
            },
            onDelete = { alarm ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_rule)
                    .setMessage(getString(R.string.super_alarm_delete_confirm, alarm.label.ifBlank {
                        getString(R.string.super_alarm_default_label)
                    }))
                    .setPositiveButton(R.string.delete_rule) { _, _ ->
                        lifecycleScope.launch { alarmManager.delete(alarm.id) }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            },
            onTry = { alarm ->
                AlarmPreviewLauncher.start(this, alarm)
            }
        )

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddAlarm.setOnClickListener {
            SuperAlarmEditDialog.newInstance()
                .show(supportFragmentManager, "edit_alarm")
        }
        binding.btnGrantExactAlarm.setOnClickListener { openExactAlarmSettings() }
        binding.btnGrantNotifications.setOnClickListener {
            PermissionHelper.openPermissionSettings(this, com.ecosentinel.appblocker.util.PermissionKind.NOTIFICATIONS)
        }

        binding.alarmsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.alarmsRecyclerView.adapter = adapter

        lifecycleScope.launch {
            alarmManager.observeAll().collectLatest { alarms ->
                adapter.submitList(alarms)
                binding.emptyAlarmsText.visibility =
                    if (alarms.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsCard()
    }

    private fun updatePermissionsCard() {
        val needsExact = !AlarmScheduler.canScheduleExactAlarms(this)
        val needsNotifications = !PermissionHelper.hasNotificationPermission(this)
        val showCard = needsExact || needsNotifications
        binding.permissionsCard.visibility = if (showCard) View.VISIBLE else View.GONE
        binding.btnGrantExactAlarm.visibility = if (needsExact) View.VISIBLE else View.GONE
        binding.btnGrantNotifications.visibility = if (needsNotifications) View.VISIBLE else View.GONE
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    override fun onAlarmSaved(alarm: SuperAlarmEntity) {
        lifecycleScope.launch {
            alarmManager.save(alarm)
            Toast.makeText(this@SuperAlarmActivity, R.string.super_alarm_saved, Toast.LENGTH_SHORT).show()
        }
    }
}

private class SuperAlarmAdapter(
    private val onToggle: (SuperAlarmEntity, Boolean) -> Unit,
    private val onEdit: (SuperAlarmEntity) -> Unit,
    private val onDelete: (SuperAlarmEntity) -> Unit,
    private val onTry: (SuperAlarmEntity) -> Unit
) : ListAdapter<SuperAlarmEntity, SuperAlarmAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<SuperAlarmEntity>() {
        override fun areItemsTheSame(oldItem: SuperAlarmEntity, newItem: SuperAlarmEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SuperAlarmEntity, newItem: SuperAlarmEntity) =
            oldItem == newItem
    }

    inner class ViewHolder(private val binding: ItemSuperAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: SuperAlarmEntity) {
            val context = binding.root.context
            binding.alarmTimeText.text = AlarmRepeatDays.formatTime(alarm.hour, alarm.minute)
            binding.alarmLabelText.text = alarm.label.ifBlank {
                context.getString(R.string.super_alarm_default_label)
            }
            binding.alarmRepeatText.text = AlarmRepeatDays.formatMask(context, alarm.repeatDaysMask)
            binding.alarmDifficultyText.text = context.getString(
                R.string.super_alarm_difficulty_value,
                difficultyLabel(context, alarm.challengeDifficulty)
            )
            binding.alarmSoundText.text = context.getString(
                R.string.super_alarm_sound_value,
                AlarmSoundHelper.displayName(context, alarm.soundUri)
            )

            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.isChecked = alarm.enabled
            binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(alarm, isChecked)
            }
            binding.btnEdit.setOnClickListener { onEdit(alarm) }
            binding.btnTry.setOnClickListener { onTry(alarm) }
            binding.btnDelete.setOnClickListener { onDelete(alarm) }
        }

        private fun difficultyLabel(
            context: android.content.Context,
            difficulty: AlarmChallengeDifficulty
        ): String {
            return when (difficulty) {
                AlarmChallengeDifficulty.EASY -> context.getString(R.string.super_alarm_difficulty_easy)
                AlarmChallengeDifficulty.HARD -> context.getString(R.string.super_alarm_difficulty_hard)
                else -> context.getString(R.string.super_alarm_difficulty_medium)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSuperAlarmBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
