package com.ecosentinel.appblocker.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.alarm.AlarmChallengeDifficulty
import com.ecosentinel.appblocker.alarm.AlarmPreviewLauncher
import com.ecosentinel.appblocker.alarm.MathChallenge
import com.ecosentinel.appblocker.alarm.MathChallengeGenerator
import com.ecosentinel.appblocker.alarm.SuperAlarmManager
import com.ecosentinel.appblocker.databinding.ActivityAlarmChallengeBinding
import com.ecosentinel.appblocker.service.AlarmRingingService
import kotlinx.coroutines.launch

class AlarmChallengeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmChallengeBinding
    private lateinit var alarmManager: SuperAlarmManager
    private var alarmId: Long = -1L
    private var challenge: MathChallenge? = null
    private var isPreviewMode: Boolean = false
    private var previewDifficulty: AlarmChallengeDifficulty = AlarmChallengeDifficulty.MEDIUM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        binding = ActivityAlarmChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        isPreviewMode = intent.getBooleanExtra(EXTRA_PREVIEW_MODE, false)
        if (alarmId < 0L && alarmId != AlarmPreviewLauncher.PREVIEW_ALARM_ID) {
            finish()
            return
        }

        alarmManager = SuperAlarmManager(this)
        setupPreviewUi()

        lifecycleScope.launch {
            val label: String
            val difficulty: AlarmChallengeDifficulty

            if (isPreviewMode) {
                label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                difficulty = parseDifficulty(intent.getStringExtra(EXTRA_DIFFICULTY))
            } else {
                val alarm = alarmManager.getById(alarmId) ?: run {
                    finish()
                    return@launch
                }
                label = alarm.label
                difficulty = alarm.challengeDifficulty
            }

            binding.alarmLabelText.text = label.ifBlank {
                getString(R.string.super_alarm_default_label)
            }
            previewDifficulty = difficulty
            newChallenge(difficulty)
        }

        binding.btnSubmit.setOnClickListener { submitAnswer() }
        binding.answerInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitAnswer()
                true
            } else {
                false
            }
        }
        binding.btnGoHome.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
        binding.btnStopPreview.setOnClickListener { stopPreviewAndFinish() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (incomingId >= 0L || incomingId == AlarmPreviewLauncher.PREVIEW_ALARM_ID) {
            alarmId = incomingId
        }
        isPreviewMode = intent.getBooleanExtra(EXTRA_PREVIEW_MODE, isPreviewMode)
        setupPreviewUi()
    }

    private fun setupPreviewUi() {
        binding.previewBanner.isVisible = isPreviewMode
        binding.btnStopPreview.isVisible = isPreviewMode
        binding.btnGoHome.isVisible = !isPreviewMode
        binding.challengeHintText.text = getString(
            if (isPreviewMode) {
                R.string.super_alarm_preview_challenge_hint
            } else {
                R.string.super_alarm_challenge_hint
            }
        )
    }

    private fun parseDifficulty(raw: String?): AlarmChallengeDifficulty {
        return runCatching {
            AlarmChallengeDifficulty.valueOf(raw ?: AlarmChallengeDifficulty.MEDIUM.name)
        }.getOrDefault(AlarmChallengeDifficulty.MEDIUM)
    }

    private fun newChallenge(difficulty: AlarmChallengeDifficulty) {
        challenge = MathChallengeGenerator.generate(difficulty)
        binding.questionText.text = challenge?.question
        binding.answerInput.text?.clear()
        binding.errorText.visibility = android.view.View.GONE
        binding.answerInput.requestFocus()
        getSystemService<InputMethodManager>()?.showSoftInput(
            binding.answerInput,
            InputMethodManager.SHOW_IMPLICIT
        )
    }

    private fun submitAnswer() {
        val current = challenge ?: return
        val answer = binding.answerInput.text?.toString()?.trim()?.toIntOrNull()
        if (answer == null) {
            binding.errorText.visibility = android.view.View.VISIBLE
            binding.errorText.text = getString(R.string.super_alarm_answer_required)
            return
        }
        if (answer != current.answer) {
            binding.errorText.visibility = android.view.View.VISIBLE
            binding.errorText.text = getString(R.string.super_alarm_wrong_answer)
            binding.answerInput.text?.clear()
            newChallenge(previewDifficulty)
            return
        }

        lifecycleScope.launch {
            if (isPreviewMode) {
                stopPreviewAndFinish()
            } else {
                AlarmRingingService.stop(this@AlarmChallengeActivity, alarmId)
                alarmManager.onAlarmDismissed(alarmId)
                finish()
            }
        }
    }

    private fun stopPreviewAndFinish() {
        AlarmRingingService.stop(this, alarmId)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isPreviewMode) {
            stopPreviewAndFinish()
        } else {
            moveTaskToBack(true)
        }
    }

    companion object {
        private const val EXTRA_ALARM_ID = "extra_alarm_id"
        private const val EXTRA_PREVIEW_MODE = "extra_preview_mode"
        private const val EXTRA_LABEL = "extra_label"
        private const val EXTRA_DIFFICULTY = "extra_difficulty"
        private const val EXTRA_VOLUME = "extra_volume"
        private const val EXTRA_VOLUME_GUARD = "extra_volume_guard"

        fun intent(context: Context, alarmId: Long): Intent {
            return Intent(context, AlarmChallengeActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
        }

        fun intentForPreview(
            context: Context,
            alarmId: Long,
            label: String,
            difficulty: String?,
            volumePercent: Int,
            volumeGuardEnabled: Boolean
        ): Intent {
            return Intent(context, AlarmChallengeActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_PREVIEW_MODE, true)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_DIFFICULTY, difficulty)
                putExtra(EXTRA_VOLUME, volumePercent)
                putExtra(EXTRA_VOLUME_GUARD, volumeGuardEnabled)
            }
        }
    }
}
