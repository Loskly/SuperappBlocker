package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.entity.TodoEntity
import com.ecosentinel.appblocker.databinding.DialogTodoEditBinding
import com.ecosentinel.appblocker.engine.BlockSchedule
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TodoEditDialog : DialogFragment() {

    interface Listener {
        fun onTodoSaved(todo: TodoEntity)
    }

    private var _binding: DialogTodoEditBinding? = null
    private val binding get() = _binding!!

    private var todoId: Long = 0L
    private var dueEnabled = false
    private var dueYear = 0
    private var dueMonth = 0
    private var dueDay = 0
    private var dueHour = 12
    private var dueMinute = 0

    private val dateLabelFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    private val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_Appbllocker)
        todoId = requireArguments().getLong(ARG_TODO_ID, 0L)
        dueEnabled = requireArguments().getBoolean(ARG_DUE_ENABLED, false)
        dueYear = requireArguments().getInt(ARG_DUE_YEAR, 0)
        dueMonth = requireArguments().getInt(ARG_DUE_MONTH, 0)
        dueDay = requireArguments().getInt(ARG_DUE_DAY, 0)
        dueHour = requireArguments().getInt(ARG_DUE_HOUR, 12)
        dueMinute = requireArguments().getInt(ARG_DUE_MINUTE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTodoEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.dialogTitle.setText(
            if (todoId == 0L) R.string.todo_add else R.string.todo_edit
        )
        binding.titleInput.setText(requireArguments().getString(ARG_TITLE).orEmpty())
        binding.noteInput.setText(requireArguments().getString(ARG_NOTE).orEmpty())

        if (dueEnabled && dueYear == 0) {
            val now = Calendar.getInstance()
            dueYear = now.get(Calendar.YEAR)
            dueMonth = now.get(Calendar.MONTH)
            dueDay = now.get(Calendar.DAY_OF_MONTH)
        }

        binding.dueEnabledSwitch.setOnCheckedChangeListener(null)
        binding.dueEnabledSwitch.isChecked = dueEnabled
        binding.dueEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            dueEnabled = isChecked
            updateDueControls()
        }

        binding.btnPickDate.setOnClickListener { showDatePicker() }
        binding.btnPickTime.setOnClickListener { showTimePicker() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveTodo() }

        updateDueControls()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateDueControls() {
        binding.btnPickDate.isEnabled = dueEnabled
        binding.btnPickTime.isEnabled = dueEnabled
        binding.btnPickDate.isVisible = dueEnabled
        binding.btnPickTime.isVisible = dueEnabled
        if (dueEnabled) {
            updateDateButton()
            updateTimeButton()
        }
    }

    private fun updateDateButton() {
        if (dueYear == 0) {
            binding.btnPickDate.text = getString(R.string.todo_pick_date)
            return
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, dueYear)
            set(Calendar.MONTH, dueMonth)
            set(Calendar.DAY_OF_MONTH, dueDay)
        }
        binding.btnPickDate.text = getString(R.string.todo_date_value, dateLabelFormat.format(cal.time))
    }

    private fun updateTimeButton() {
        binding.btnPickTime.text = getString(
            R.string.todo_time_value,
            BlockSchedule.formatTime(dueHour, dueMinute)
        )
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.todo_date_picker_title)
            .setSelection(currentDueDateUtcMillis())
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            utcCalendar.timeInMillis = selection
            dueYear = utcCalendar.get(Calendar.YEAR)
            dueMonth = utcCalendar.get(Calendar.MONTH)
            dueDay = utcCalendar.get(Calendar.DAY_OF_MONTH)
            updateDateButton()
        }
        picker.show(parentFragmentManager, "todo_date")
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(dueHour)
            .setMinute(dueMinute)
            .setTitleText(R.string.todo_time_picker_title)
            .build()

        picker.addOnPositiveButtonClickListener {
            dueHour = picker.hour
            dueMinute = picker.minute
            updateTimeButton()
        }
        picker.show(parentFragmentManager, "todo_time")
    }

    private fun currentDueDateUtcMillis(): Long {
        val cal = if (dueYear == 0) {
            Calendar.getInstance()
        } else {
            Calendar.getInstance().apply {
                set(Calendar.YEAR, dueYear)
                set(Calendar.MONTH, dueMonth)
                set(Calendar.DAY_OF_MONTH, dueDay)
            }
        }
        utcCalendar.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        utcCalendar.set(Calendar.MILLISECOND, 0)
        return utcCalendar.timeInMillis
    }

    private fun buildDueAtMillis(): Long {
        if (!dueEnabled || dueYear == 0) {
            return 0L
        }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, dueYear)
            set(Calendar.MONTH, dueMonth)
            set(Calendar.DAY_OF_MONTH, dueDay)
            set(Calendar.HOUR_OF_DAY, dueHour)
            set(Calendar.MINUTE, dueMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun saveTodo() {
        val title = binding.titleInput.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), R.string.todo_title_required, Toast.LENGTH_SHORT).show()
            return
        }

        val note = binding.noteInput.text?.toString()?.trim().orEmpty()
        val dueAtMillis = buildDueAtMillis()
        val wasCompleted = requireArguments().getBoolean(ARG_COMPLETED, false)

        val todo = TodoEntity(
            id = todoId,
            title = title,
            note = note,
            dueAtMillis = dueAtMillis,
            completed = wasCompleted,
            createdAtMillis = requireArguments().getLong(ARG_CREATED_AT, System.currentTimeMillis()),
            completedAtMillis = requireArguments().getLong(ARG_COMPLETED_AT, 0L)
        )

        (parentFragment as? Listener ?: activity as? Listener)?.onTodoSaved(todo)
        dismiss()
    }

    companion object {
        private const val ARG_TODO_ID = "todo_id"
        private const val ARG_TITLE = "title"
        private const val ARG_NOTE = "note"
        private const val ARG_DUE_ENABLED = "due_enabled"
        private const val ARG_DUE_YEAR = "due_year"
        private const val ARG_DUE_MONTH = "due_month"
        private const val ARG_DUE_DAY = "due_day"
        private const val ARG_DUE_HOUR = "due_hour"
        private const val ARG_DUE_MINUTE = "due_minute"
        private const val ARG_COMPLETED = "completed"
        private const val ARG_CREATED_AT = "created_at"
        private const val ARG_COMPLETED_AT = "completed_at"

        fun newInstance(todo: TodoEntity? = null): TodoEditDialog {
            return TodoEditDialog().apply {
                arguments = Bundle().apply {
                    if (todo != null) {
                        putLong(ARG_TODO_ID, todo.id)
                        putString(ARG_TITLE, todo.title)
                        putString(ARG_NOTE, todo.note)
                        putBoolean(ARG_COMPLETED, todo.completed)
                        putLong(ARG_CREATED_AT, todo.createdAtMillis)
                        putLong(ARG_COMPLETED_AT, todo.completedAtMillis)
                        if (todo.dueAtMillis > 0L) {
                            val cal = Calendar.getInstance().apply { timeInMillis = todo.dueAtMillis }
                            putBoolean(ARG_DUE_ENABLED, true)
                            putInt(ARG_DUE_YEAR, cal.get(Calendar.YEAR))
                            putInt(ARG_DUE_MONTH, cal.get(Calendar.MONTH))
                            putInt(ARG_DUE_DAY, cal.get(Calendar.DAY_OF_MONTH))
                            putInt(ARG_DUE_HOUR, cal.get(Calendar.HOUR_OF_DAY))
                            putInt(ARG_DUE_MINUTE, cal.get(Calendar.MINUTE))
                        } else {
                            putBoolean(ARG_DUE_ENABLED, false)
                        }
                    }
                }
            }
        }
    }
}

object TodoUiHelper {
    private val dueFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru"))

    fun formatDue(millis: Long): String? {
        if (millis <= 0L) {
            return null
        }
        return dueFormat.format(Date(millis))
    }
}
