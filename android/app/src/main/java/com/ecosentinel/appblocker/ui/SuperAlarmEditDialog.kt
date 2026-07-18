package com.ecosentinel.appblocker.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.alarm.AlarmChallengeDifficulty
import com.ecosentinel.appblocker.alarm.AlarmPreviewLauncher
import com.ecosentinel.appblocker.alarm.AlarmRepeatDays
import com.ecosentinel.appblocker.alarm.AlarmSoundHelper
import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import com.ecosentinel.appblocker.databinding.DialogSuperAlarmEditBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat

class SuperAlarmEditDialog : DialogFragment() {

    interface Listener {
        fun onAlarmSaved(alarm: SuperAlarmEntity)
    }

    private var _binding: DialogSuperAlarmEditBinding? = null
    private val binding get() = _binding!!

    private var alarmId: Long = 0L
    private var hour: Int = 7
    private var minute: Int = 0
    private var selectedSoundUri: String = ""
    private var selectedSoundDisplayName: String = ""

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        val uri = readPickedRingtoneUri(result.data) ?: return@registerForActivityResult
        applyPickedSound(uri)
    }

    private val audioFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        applyPickedSound(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_Appbllocker)
        alarmId = requireArguments().getLong(ARG_ALARM_ID, 0L)
        hour = requireArguments().getInt(ARG_HOUR, 7)
        minute = requireArguments().getInt(ARG_MINUTE, 0)
        selectedSoundUri = requireArguments().getString(ARG_SOUND_URI).orEmpty()
        selectedSoundDisplayName = requireArguments().getString(ARG_SOUND_DISPLAY_NAME).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSuperAlarmEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.labelInput.setText(requireArguments().getString(ARG_LABEL).orEmpty())
        binding.volumeSlider.value = requireArguments().getInt(ARG_VOLUME, 100).toFloat()
        binding.volumeGuardSwitch.isChecked = requireArguments().getBoolean(ARG_VOLUME_GUARD, true)
        updateTimeButton()
        updateSoundUi()

        val repeatMask = requireArguments().getInt(ARG_REPEAT_MASK, AlarmRepeatDays.WEEKDAYS)
        setChipChecked(binding.chipMon, repeatMask and AlarmRepeatDays.MONDAY != 0)
        setChipChecked(binding.chipTue, repeatMask and AlarmRepeatDays.TUESDAY != 0)
        setChipChecked(binding.chipWed, repeatMask and AlarmRepeatDays.WEDNESDAY != 0)
        setChipChecked(binding.chipThu, repeatMask and AlarmRepeatDays.THURSDAY != 0)
        setChipChecked(binding.chipFri, repeatMask and AlarmRepeatDays.FRIDAY != 0)
        setChipChecked(binding.chipSat, repeatMask and AlarmRepeatDays.SATURDAY != 0)
        setChipChecked(binding.chipSun, repeatMask and AlarmRepeatDays.SUNDAY != 0)

        when (AlarmChallengeDifficulty.valueOf(
            requireArguments().getString(ARG_DIFFICULTY, AlarmChallengeDifficulty.MEDIUM.name)
        )) {
            AlarmChallengeDifficulty.EASY -> binding.radioEasy.isChecked = true
            AlarmChallengeDifficulty.HARD -> binding.radioHard.isChecked = true
            else -> binding.radioMedium.isChecked = true
        }

        binding.btnPickTime.setOnClickListener { showTimePicker() }
        binding.btnPickSound.setOnClickListener { showSoundPickerDialog() }
        binding.btnResetSound.setOnClickListener {
            selectedSoundUri = ""
            selectedSoundDisplayName = ""
            updateSoundUi()
        }
        binding.btnTry.setOnClickListener { startPreview() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveAlarm() }
    }

    private fun setChipChecked(chip: Chip, checked: Boolean) {
        chip.isChecked = checked
    }

    private fun updateTimeButton() {
        binding.btnPickTime.text = getString(
            R.string.super_alarm_time_value,
            AlarmRepeatDays.formatTime(hour, minute)
        )
    }

    private fun updateSoundUi() {
        binding.soundNameText.text = AlarmSoundHelper.displayName(
            requireContext(),
            selectedSoundUri,
            selectedSoundDisplayName
        )
        binding.btnResetSound.isVisible = selectedSoundUri.isNotBlank()
    }

    private fun applyPickedSound(uri: Uri) {
        val persisted = AlarmSoundHelper.persistPickedSound(requireContext(), uri)
        selectedSoundUri = persisted.uri
        selectedSoundDisplayName = persisted.displayName
        updateSoundUi()
    }

    private fun showSoundPickerDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.super_alarm_sound_pick_title)
            .setItems(
                arrayOf(
                    getString(R.string.super_alarm_pick_ringtone),
                    getString(R.string.super_alarm_pick_file)
                )
            ) { _, which ->
                when (which) {
                    0 -> ringtonePickerLauncher.launch(
                        AlarmSoundHelper.ringtonePickerIntent(requireContext(), selectedSoundUri)
                    )
                    1 -> audioFileLauncher.launch(arrayOf("audio/*"))
                }
            }
            .show()
    }

    private fun readPickedRingtoneUri(data: Intent?): Uri? {
        if (data == null) {
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(getString(R.string.super_alarm_pick_time))
            .build()
        picker.addOnPositiveButtonClickListener {
            hour = picker.hour
            minute = picker.minute
            updateTimeButton()
        }
        picker.show(parentFragmentManager, "alarm_time")
    }

    private fun collectRepeatMask(): Int {
        var mask = 0
        if (binding.chipMon.isChecked) mask = mask or AlarmRepeatDays.MONDAY
        if (binding.chipTue.isChecked) mask = mask or AlarmRepeatDays.TUESDAY
        if (binding.chipWed.isChecked) mask = mask or AlarmRepeatDays.WEDNESDAY
        if (binding.chipThu.isChecked) mask = mask or AlarmRepeatDays.THURSDAY
        if (binding.chipFri.isChecked) mask = mask or AlarmRepeatDays.FRIDAY
        if (binding.chipSat.isChecked) mask = mask or AlarmRepeatDays.SATURDAY
        if (binding.chipSun.isChecked) mask = mask or AlarmRepeatDays.SUNDAY
        return mask
    }

    private fun selectedDifficulty(): AlarmChallengeDifficulty {
        return when {
            binding.radioEasy.isChecked -> AlarmChallengeDifficulty.EASY
            binding.radioHard.isChecked -> AlarmChallengeDifficulty.HARD
            else -> AlarmChallengeDifficulty.MEDIUM
        }
    }

    private fun saveAlarm() {
        (parentFragment as? Listener ?: activity as? Listener)?.onAlarmSaved(buildAlarmEntity())
        dismiss()
    }

    private fun startPreview() {
        val alarm = buildAlarmEntity()
        dismiss()
        AlarmPreviewLauncher.start(requireContext(), alarm)
    }

    private fun buildAlarmEntity(): SuperAlarmEntity {
        val label = binding.labelInput.text?.toString()?.trim().orEmpty()
        val (soundUri, soundDisplayName) = finalizeSoundForSave()
        return SuperAlarmEntity(
            id = alarmId,
            hour = hour,
            minute = minute,
            repeatDaysMask = collectRepeatMask(),
            label = label,
            enabled = true,
            challengeDifficulty = selectedDifficulty(),
            volumePercent = binding.volumeSlider.value.toInt(),
            volumeGuardEnabled = binding.volumeGuardSwitch.isChecked,
            soundUri = soundUri,
            soundDisplayName = soundDisplayName
        )
    }

    private fun finalizeSoundForSave(): Pair<String, String> {
        if (selectedSoundUri.isBlank()) {
            return "" to ""
        }
        val parsed = Uri.parse(selectedSoundUri)
        if (parsed.scheme == "file") {
            val name = selectedSoundDisplayName.ifBlank {
                AlarmSoundHelper.displayName(requireContext(), selectedSoundUri, null)
            }
            return selectedSoundUri to name
        }
        val persisted = AlarmSoundHelper.persistPickedSound(requireContext(), parsed)
        return persisted.uri to persisted.displayName
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ALARM_ID = "alarm_id"
        private const val ARG_LABEL = "label"
        private const val ARG_HOUR = "hour"
        private const val ARG_MINUTE = "minute"
        private const val ARG_REPEAT_MASK = "repeat_mask"
        private const val ARG_DIFFICULTY = "difficulty"
        private const val ARG_VOLUME = "volume"
        private const val ARG_VOLUME_GUARD = "volume_guard"
        private const val ARG_SOUND_URI = "sound_uri"
        private const val ARG_SOUND_DISPLAY_NAME = "sound_display_name"

        fun newInstance(alarm: SuperAlarmEntity? = null): SuperAlarmEditDialog {
            return SuperAlarmEditDialog().apply {
                arguments = Bundle().apply {
                    if (alarm != null) {
                        putLong(ARG_ALARM_ID, alarm.id)
                        putString(ARG_LABEL, alarm.label)
                        putInt(ARG_HOUR, alarm.hour)
                        putInt(ARG_MINUTE, alarm.minute)
                        putInt(ARG_REPEAT_MASK, alarm.repeatDaysMask)
                        putString(ARG_DIFFICULTY, alarm.challengeDifficulty.name)
                        putInt(ARG_VOLUME, alarm.volumePercent)
                        putBoolean(ARG_VOLUME_GUARD, alarm.volumeGuardEnabled)
                        putString(ARG_SOUND_URI, alarm.soundUri)
                        putString(ARG_SOUND_DISPLAY_NAME, alarm.soundDisplayName)
                    } else {
                        putInt(ARG_REPEAT_MASK, AlarmRepeatDays.WEEKDAYS)
                        putString(ARG_DIFFICULTY, AlarmChallengeDifficulty.MEDIUM.name)
                        putInt(ARG_VOLUME, 100)
                        putBoolean(ARG_VOLUME_GUARD, true)
                        putString(ARG_SOUND_URI, "")
                        putString(ARG_SOUND_DISPLAY_NAME, "")
                    }
                }
            }
        }
    }
}
