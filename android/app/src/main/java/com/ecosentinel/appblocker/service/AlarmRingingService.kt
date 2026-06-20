package com.ecosentinel.appblocker.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.ecosentinel.appblocker.alarm.AlarmPreviewLauncher
import com.ecosentinel.appblocker.alarm.AlarmSoundHelper
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import com.ecosentinel.appblocker.ui.AlarmChallengeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmRingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmId: Long = -1L
    private var alarmEntity: SuperAlarmEntity? = null
    private var targetVolume: Int = 0
    private var volumeGuardEnabled: Boolean = true

    private val volumeGuardRunnable = object : Runnable {
        override fun run() {
            if (!volumeGuardEnabled) {
                return
            }
            val audioManager = getSystemService<AudioManager>() ?: return
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (current < targetVolume) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    targetVolume,
                    0
                )
            }
            handler.postDelayed(this, VOLUME_GUARD_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val stopId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                if (stopId == alarmId || alarmId < 0L) {
                    stopRinging()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        val incomingId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        if (incomingId < 0L && incomingId != AlarmPreviewLauncher.PREVIEW_ALARM_ID) {
            stopSelf()
            return START_NOT_STICKY
        }

        val isPreview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true

        if (alarmId == incomingId && mediaPlayer?.isPlaying == true) {
            launchChallengeScreen(incomingId, isPreview, intent)
            return START_STICKY
        }

        alarmId = incomingId
        scope.launch {
            val alarm = if (isPreview) {
                previewAlarmFromIntent(incomingId, intent)
            } else {
                AppDatabase.getInstance(applicationContext).superAlarmDao().getById(alarmId)
            }
            if (alarm == null) {
                stopSelf()
                return@launch
            }
            alarmEntity = alarm
            startRinging(alarm, isPreview)
        }
        return START_STICKY
    }

    private fun previewAlarmFromIntent(alarmId: Long, intent: Intent?): SuperAlarmEntity {
        val difficulty = runCatching {
            com.ecosentinel.appblocker.alarm.AlarmChallengeDifficulty.valueOf(
                intent?.getStringExtra(EXTRA_DIFFICULTY)
                    ?: com.ecosentinel.appblocker.alarm.AlarmChallengeDifficulty.MEDIUM.name
            )
        }.getOrDefault(com.ecosentinel.appblocker.alarm.AlarmChallengeDifficulty.MEDIUM)

        return SuperAlarmEntity(
            id = alarmId,
            hour = 0,
            minute = 0,
            repeatDaysMask = 0,
            label = intent?.getStringExtra(EXTRA_LABEL).orEmpty(),
            enabled = true,
            challengeDifficulty = difficulty,
            volumePercent = intent?.getIntExtra(EXTRA_VOLUME, 100) ?: 100,
            volumeGuardEnabled = intent?.getBooleanExtra(EXTRA_VOLUME_GUARD, true) ?: true,
            soundUri = intent?.getStringExtra(EXTRA_SOUND_URI).orEmpty()
        )
    }

    private fun startRinging(alarm: SuperAlarmEntity, isPreview: Boolean = false) {
        acquireWakeLock()
        applyAlarmVolume(alarm)
        startVibration()
        startSound(alarm)
        volumeGuardEnabled = alarm.volumeGuardEnabled
        if (volumeGuardEnabled) {
            handler.post(volumeGuardRunnable)
        }

        val notification = AlarmNotificationHelper.buildRingingNotification(
            context = this,
            alarmId = alarm.id,
            label = alarm.label,
            isPreview = isPreview
        )
        startForeground(AlarmNotificationHelper.notificationId(alarm.id), notification)
        launchChallengeScreen(alarm.id, isPreview, null)
    }

    private fun launchChallengeScreen(id: Long, isPreview: Boolean, intent: Intent?) {
        val challengeIntent = if (isPreview) {
            val alarm = alarmEntity
            AlarmChallengeActivity.intentForPreview(
                context = this,
                alarmId = id,
                label = intent?.getStringExtra(EXTRA_LABEL) ?: alarm?.label.orEmpty(),
                difficulty = intent?.getStringExtra(EXTRA_DIFFICULTY)
                    ?: alarm?.challengeDifficulty?.name,
                volumePercent = intent?.getIntExtra(EXTRA_VOLUME, -1)
                    ?.takeIf { it >= 0 }
                    ?: alarm?.volumePercent
                    ?: 100,
                volumeGuardEnabled = intent?.getBooleanExtra(EXTRA_VOLUME_GUARD, alarm?.volumeGuardEnabled ?: true)
                    ?: true
            )
        } else {
            AlarmChallengeActivity.intent(this, id)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(challengeIntent)
    }

    private fun applyAlarmVolume(alarm: SuperAlarmEntity) {
        val audioManager = getSystemService<AudioManager>() ?: return
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val percent = alarm.volumePercent.coerceIn(10, 100)
        targetVolume = (max * percent / 100f).toInt().coerceAtLeast(1)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
    }

    private fun startSound(alarm: SuperAlarmEntity) {
        stopSound()
        val uri = AlarmSoundHelper.resolvePlaybackUri(alarm.soundUri)
            ?: AlarmSoundHelper.defaultSystemUri()
            ?: return

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                setVolume(1f, 1f)
                prepare()
                start()
            }
        } catch (_: Exception) {
            val fallback = AlarmSoundHelper.defaultSystemUri() ?: return
            if (fallback == uri) {
                return
            }
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(applicationContext, fallback)
                    isLooping = true
                    setVolume(1f, 1f)
                    prepare()
                    start()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        } ?: return

        val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }
        vibrator?.cancel()
    }

    private fun stopSound() {
        mediaPlayer?.run {
            try {
                if (isPlaying) stop()
                release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService<PowerManager>() ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Appbllocker:SuperAlarm"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun stopRinging() {
        handler.removeCallbacks(volumeGuardRunnable)
        stopSound()
        stopVibration()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopRinging()
        if (alarmId >= 0L) {
            activeAlarmId = -1L
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ALARM_ID = "extra_alarm_id"
        private const val EXTRA_PREVIEW = "extra_preview"
        private const val EXTRA_LABEL = "extra_label"
        private const val EXTRA_DIFFICULTY = "extra_difficulty"
        private const val EXTRA_VOLUME = "extra_volume"
        private const val EXTRA_VOLUME_GUARD = "extra_volume_guard"
        private const val EXTRA_SOUND_URI = "extra_sound_uri"
        private const val ACTION_STOP = "com.ecosentinel.appblocker.STOP_ALARM"
        private const val VOLUME_GUARD_INTERVAL_MS = 500L

        @Volatile
        var activeAlarmId: Long = -1L
            private set

        fun start(context: Context, alarmId: Long) {
            activeAlarmId = alarmId
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startPreview(context: Context, previewAlarmId: Long, alarm: SuperAlarmEntity) {
            activeAlarmId = previewAlarmId
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                putExtra(EXTRA_ALARM_ID, previewAlarmId)
                putExtra(EXTRA_PREVIEW, true)
                putExtra(EXTRA_LABEL, alarm.label)
                putExtra(EXTRA_DIFFICULTY, alarm.challengeDifficulty.name)
                putExtra(EXTRA_VOLUME, alarm.volumePercent)
                putExtra(EXTRA_VOLUME_GUARD, alarm.volumeGuardEnabled)
                putExtra(EXTRA_SOUND_URI, alarm.soundUri)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
            context.startService(intent)
        }

        fun isRinging(alarmId: Long): Boolean = activeAlarmId == alarmId
    }
}
