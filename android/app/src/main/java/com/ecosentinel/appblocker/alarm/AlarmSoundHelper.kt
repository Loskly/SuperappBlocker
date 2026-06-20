package com.ecosentinel.appblocker.alarm

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import com.ecosentinel.appblocker.R

object AlarmSoundHelper {

    fun resolvePlaybackUri(soundUri: String?): Uri? {
        if (!soundUri.isNullOrBlank()) {
            return Uri.parse(soundUri)
        }
        return defaultSystemUri()
    }

    fun defaultSystemUri(): Uri? {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    fun displayName(context: Context, soundUri: String?): String {
        if (soundUri.isNullOrBlank()) {
            return context.getString(R.string.super_alarm_sound_default)
        }

        val uri = Uri.parse(soundUri)
        queryDisplayName(context, uri)?.let { return it }

        val ringtone = RingtoneManager.getRingtone(context, uri)
        val title = ringtone?.getTitle(context)
        if (!title.isNullOrBlank()) {
            return title
        }

        return context.getString(R.string.super_alarm_sound_custom)
    }

    fun ringtonePickerIntent(context: Context, existingUri: String?): Intent {
        val existing = existingUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.super_alarm_pick_ringtone))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing ?: defaultSystemUri())
        }
    }

    fun audioFilePickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    fun persistReadPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") {
            return null
        }
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0) {
                return@use null
            }
            cursor.getString(index)?.takeIf { it.isNotBlank() }
        }
    }
}
