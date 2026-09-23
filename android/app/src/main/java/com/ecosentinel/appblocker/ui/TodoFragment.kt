package com.ecosentinel.appblocker.ui

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.TodoEntity
import com.ecosentinel.appblocker.databinding.FragmentTodoBinding
import com.ecosentinel.appblocker.databinding.ItemTodoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TodoFragment : Fragment(), TodoEditDialog.Listener {

    private var _binding: FragmentTodoBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TodoAdapter
    private val todoDao by lazy { AppDatabase.getInstance(requireContext()).todoDao() }
    private val unlockGrantDao by lazy { AppDatabase.getInstance(requireContext()).unlockGrantDao() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TodoAdapter(
            onToggleCompleted = { todo, completed ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val updatedTodo = todo.copy(
                        completed = completed,
                        completedAtMillis = if (completed) System.currentTimeMillis() else 0L
                    )
                    todoDao.upsert(updatedTodo)

                    if (completed && todo.rewardMinutes > 0 && todo.unlockAppPackage.isNotBlank()) {
                        val grant = com.ecosentinel.appblocker.data.entity.UnlockGrantEntity(
                            packageName = todo.unlockAppPackage,
                            source = "TODO_REWARD",
                            grantedUntilMillis = System.currentTimeMillis() + (todo.rewardMinutes * 60_000L),
                            metadataJson = null
                        )
                        unlockGrantDao.insert(grant)
                        Toast.makeText(requireContext(), "+${todo.rewardMinutes} min for ${todo.unlockAppPackage}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onEdit = { todo ->
                TodoEditDialog.newInstance(todo).show(childFragmentManager, "todo_edit")
            },
            onDelete = { todo ->
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.todo_delete_confirm, todo.title))
                    .setPositiveButton(R.string.delete_rule) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            todoDao.deleteById(todo.id)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )

        binding.todosRecyclerView.prepareForScrollParent(requireContext())
        binding.todosRecyclerView.adapter = adapter
        binding.btnAddTodo.setOnClickListener {
            TodoEditDialog.newInstance().show(childFragmentManager, "todo_add")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            resetDailyTodosIfNeeded()
            todoDao.observeAllWithSubtasks().collectLatest { todos ->
                adapter.submitListRemeasure(binding.todosRecyclerView, todos)
                binding.emptyTodosText.isVisible = todos.isEmpty()
            }
        }
    }

    private suspend fun resetDailyTodosIfNeeded() {
        val todos = todoDao.observeAllWithSubtasks().first()
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        for (todoWithSubtasks in todos) {
            val todo = todoWithSubtasks.todo
            if (todo.completed && todo.recurrenceRule == "DAILY" && todo.completedAtMillis < startOfDay) {
                todoDao.upsert(todo.copy(completed = false, completedAtMillis = 0L))
            }
        }
    }

    override fun onTodoSaved(todo: TodoEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            todoDao.upsert(todo)
            Toast.makeText(requireContext(), R.string.todo_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class TodoAdapter(
    private val onToggleCompleted: (TodoEntity, Boolean) -> Unit,
    private val onEdit: (TodoEntity) -> Unit,
    private val onDelete: (TodoEntity) -> Unit
) : ListAdapter<com.ecosentinel.appblocker.data.entity.TodoWithSubtasks, TodoAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<com.ecosentinel.appblocker.data.entity.TodoWithSubtasks>() {
        override fun areItemsTheSame(oldItem: com.ecosentinel.appblocker.data.entity.TodoWithSubtasks, newItem: com.ecosentinel.appblocker.data.entity.TodoWithSubtasks) = oldItem.todo.id == newItem.todo.id
        override fun areContentsTheSame(oldItem: com.ecosentinel.appblocker.data.entity.TodoWithSubtasks, newItem: com.ecosentinel.appblocker.data.entity.TodoWithSubtasks) = oldItem == newItem
    }

    inner class ViewHolder(
        private val binding: ItemTodoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(todoWithSubtasks: com.ecosentinel.appblocker.data.entity.TodoWithSubtasks) {
            val todo = todoWithSubtasks.todo
            binding.todoTitleText.text = todo.title
            binding.todoTitleText.paintFlags = if (todo.completed) {
                binding.todoTitleText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.todoTitleText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.todoTitleText.alpha = if (todo.completed) 0.6f else 1f

            val dueText = TodoUiHelper.formatDue(todo.dueAtMillis)
            binding.todoDueText.isVisible = dueText != null
            binding.todoDueText.text = dueText?.let {
                binding.root.context.getString(R.string.todo_due_label, it)
            }

            binding.todoNoteText.isVisible = todo.note.isNotBlank()
            binding.todoNoteText.text = todo.note

            binding.completedCheckBox.setOnCheckedChangeListener(null)
            binding.completedCheckBox.isChecked = todo.completed
            binding.completedCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != todo.completed) {
                    onToggleCompleted(todo, isChecked)
                }
            }

            binding.todoTagsText.isVisible = todo.tags.isNotBlank()
            binding.todoTagsText.text = todo.tags

            binding.todoRewardText.isVisible = todo.rewardMinutes > 0 && todo.unlockAppPackage.isNotBlank()
            binding.todoRewardText.text = "🎁 +${todo.rewardMinutes}m ${todo.unlockAppPackage}"

            binding.todoStrictText.isVisible = todo.isStrictBlock

            binding.todoMetaLayout.isVisible = binding.todoTagsText.isVisible || binding.todoRewardText.isVisible || binding.todoStrictText.isVisible

            val strokeColor = when(todo.priority) {
                2 -> android.graphics.Color.parseColor("#FF5252") // High
                1 -> android.graphics.Color.parseColor("#FFD740") // Medium
                else -> android.graphics.Color.parseColor("#4CAF50") // Low / Default
            }
            (binding.root as? com.google.android.material.card.MaterialCardView)?.strokeColor = strokeColor

            binding.root.setOnClickListener { onEdit(todo) }
            binding.btnDeleteTodo.setOnClickListener { onDelete(todo) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTodoBinding.inflate(
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
