package com.ecosentinel.appblocker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.calories.CalorieTrackerRepository
import com.ecosentinel.appblocker.data.entity.FoodEntryEntity
import com.ecosentinel.appblocker.databinding.FragmentCaloriesBinding
import com.ecosentinel.appblocker.databinding.ItemFoodEntryBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class CaloriesFragment : Fragment() {

    private var _binding: FragmentCaloriesBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: CalorieTrackerRepository
    private lateinit var adapter: FoodEntryAdapter

    private var pendingPhotoPath: String? = null
    private var cameraPhotoFile: File? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val imported = repository.importPhoto(uri)
            if (imported == null) {
                return@launch
            }
            replacePendingPhoto(imported)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = cameraPhotoFile
        cameraPhotoFile = null
        if (!success || file == null) {
            file?.delete()
            return@registerForActivityResult
        }
        viewLifecycleOwner.lifecycleScope.launch {
            replacePendingPhoto(repository.relativePathForFile(file))
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            Toast.makeText(requireContext(), R.string.calories_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCaloriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = CalorieTrackerRepository(requireContext())

        pendingPhotoPath = savedInstanceState?.getString(STATE_PENDING_PHOTO_PATH)

        adapter = FoodEntryAdapter(repository)
        binding.foodEntriesRecyclerView.prepareForScrollParent(requireContext())
        binding.foodEntriesRecyclerView.adapter = adapter
        attachSwipeToDelete(binding.foodEntriesRecyclerView)

        binding.btnAddFood.setOnClickListener { addFoodEntry() }
        binding.btnPhotoCamera.setOnClickListener { requestCameraAndCapture() }
        binding.btnPhotoGallery.setOnClickListener { openGallery() }
        binding.btnRemovePhoto.setOnClickListener { clearPendingPhoto() }

        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                repository.observeTodayEntries(),
                repository.observeTodayTotalCalories()
            ) { entries, total ->
                entries to total
            }.collect { (entries, total) ->
                adapter.submitListRemeasure(binding.foodEntriesRecyclerView, entries)
                binding.emptyEntriesText.isVisible = entries.isEmpty()
                binding.todayTotalValue.text = total.toString()
            }
        }

        updatePhotoPreview()
        refreshRecentChips()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPhotoPath?.let { outState.putString(STATE_PENDING_PHOTO_PATH, it) }
    }

    override fun onResume() {
        super.onResume()
        refreshRecentChips()
    }

    private fun addFoodEntry() {
        val name = binding.foodNameInput.text?.toString()?.trim().orEmpty()
        val caloriesText = binding.caloriesInput.text?.toString()?.trim().orEmpty()
        val calories = caloriesText.toIntOrNull()

        when {
            name.isEmpty() -> {
                binding.foodNameInput.error = getString(R.string.calories_error_name)
                return
            }
            calories == null || calories <= 0 -> {
                binding.caloriesInput.error = getString(R.string.calories_error_amount)
                return
            }
        }

        binding.foodNameInput.error = null
        binding.caloriesInput.error = null

        val resolvedCalories = calories ?: return
        val photoPath = pendingPhotoPath.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            repository.addEntry(name, resolvedCalories, photoPath)
            binding.foodNameInput.text?.clear()
            binding.caloriesInput.text?.clear()
            pendingPhotoPath = null
            updatePhotoPreview()
            refreshRecentChips()
        }
    }

    private fun fillFromRecent(entry: FoodEntryEntity) {
        binding.foodNameInput.setText(entry.name)
        binding.caloriesInput.setText(entry.calories.toString())
        binding.foodNameInput.error = null
        binding.caloriesInput.error = null
        clearPendingPhoto()
    }

    private fun requestCameraAndCapture() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> launchCameraCapture()
            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraCapture() {
        val file = repository.createCameraPhotoFile()
        cameraPhotoFile = file
        takePictureLauncher.launch(repository.fileProviderUri(file))
    }

    private fun openGallery() {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private suspend fun replacePendingPhoto(newPath: String) {
        val oldPath = pendingPhotoPath
        pendingPhotoPath = newPath
        if (!oldPath.isNullOrBlank() && oldPath != newPath) {
            repository.deletePhotoByPath(oldPath)
        }
        updatePhotoPreview()
    }

    private fun clearPendingPhoto() {
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (!path.isNullOrBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                repository.deletePhotoByPath(path)
                updatePhotoPreview()
            }
        } else {
            updatePhotoPreview()
        }
    }

    private fun updatePhotoPreview() {
        val file = pendingPhotoPath?.let(repository::resolvePhotoFile)
        binding.photoPreview.isVisible = file != null
        binding.btnRemovePhoto.isVisible = file != null
        if (file != null) {
            binding.photoPreview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            binding.photoPreview.setImageDrawable(null)
        }
    }

    private fun refreshRecentChips() {
        viewLifecycleOwner.lifecycleScope.launch {
            val recent = repository.getRecentUniqueFoods()
            binding.recentFoodLabel.isVisible = recent.isNotEmpty()
            binding.recentFoodChips.isVisible = recent.isNotEmpty()
            binding.recentFoodChips.removeAllViews()
            recent.forEach { entry ->
                val chip = Chip(requireContext(), null, R.style.Widget_Appbllocker_Chip).apply {
                    text = "${entry.name} · ${entry.calories}"
                    isCheckable = false
                    setOnClickListener { fillFromRecent(entry) }
                }
                binding.recentFoodChips.addView(chip)
            }
        }
    }

    private fun attachSwipeToDelete(recyclerView: RecyclerView) {
        val deleteBackground = ColorDrawable(Color.parseColor("#B3261E"))
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    return
                }
                val entry = adapter.currentList[position]
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.deleteEntry(entry.id)
                    refreshRecentChips()
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                if (dX < 0) {
                    deleteBackground.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                    deleteBackground.draw(c)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        helper.attachToRecyclerView(recyclerView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class FoodEntryAdapter(
        private val repository: CalorieTrackerRepository
    ) : ListAdapter<FoodEntryEntity, FoodEntryAdapter.ViewHolder>(Diff) {

        object Diff : DiffUtil.ItemCallback<FoodEntryEntity>() {
            override fun areItemsTheSame(oldItem: FoodEntryEntity, newItem: FoodEntryEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FoodEntryEntity, newItem: FoodEntryEntity) =
                oldItem == newItem
        }

        inner class ViewHolder(
            private val binding: ItemFoodEntryBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(entry: FoodEntryEntity) {
                binding.entryLineText.text = repository.formatEntryLine(entry)
                val file = repository.resolvePhotoFile(entry.photoPath)
                binding.entryPhoto.isVisible = file != null
                if (file != null) {
                    binding.entryPhoto.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                } else {
                    binding.entryPhoto.setImageDrawable(null)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFoodEntryBinding.inflate(
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

    companion object {
        private const val STATE_PENDING_PHOTO_PATH = "pending_photo_path"
    }
}
