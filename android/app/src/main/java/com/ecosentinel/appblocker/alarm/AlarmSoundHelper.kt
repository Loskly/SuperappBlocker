package com.ecosentinel.appblocker.alarm

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.ecosentinel.appblocker.R
import java.io.File
import java.util.UUID

object AlarmSoundHelper {

    data class PersistedSound(
        val uri: String,
        val displayName: String
    )

    fun resolvePlaybackUri(context: Context, soundUri: String?): Uri? {
        if (soundUri.isNullOrBlank()) {
            return defaultSystemUri()
        }
        val uri = Uri.parse(soundUri)
        if (uri.scheme == "file") {
            val path = uri.path ?: return defaultSystemUri()
            return if (File(path).exists()) uri else defaultSystemUri()
        }
        if (uri.scheme == "content" && isContentUriReadable(context, uri)) {
            return uri
        }
        return defaultSystemUri()
    }

    fun defaultSystemUri(): Uri? {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    fun displayName(context: Context, soundUri: String?, soundDisplayName: String? = null): String {
        if (soundUri.isNullOrBlank()) {
            return context.getString(R.string.super_alarm_sound_default)
        }

        soundDisplayName?.takeIf { isMeaningfulTitle(it) }?.let { return it }

        val uri = Uri.parse(soundUri)
        resolveDisplayName(context, uri)?.let { return it }

        return context.getString(R.string.super_alarm_sound_custom)
    }

    fun resolveDisplayName(context: Context, uri: Uri): String? {
        queryMediaStoreTitle(context, uri)?.let { return it }
        queryOpenableDisplayName(context, uri)?.let { return it }

        val ringtoneTitle = runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()
        if (isMeaningfulTitle(ringtoneTitle)) {
            return ringtoneTitle
        }

        return null
    }

    /**
     * Stores picked sounds durably: document/downloads URIs are copied into app storage so playback
     * survives hibernation and revoked content permissions; MediaStore ringtone URIs are kept as-is.
     */
    fun persistPickedSound(context: Context, uri: Uri): PersistedSound {
        persistReadPermission(context, uri)
        val displayName = resolveDisplayName(context, uri)
            ?: context.getString(R.string.super_alarm_sound_custom)

        if (shouldImportToInternalStorage(uri)) {
            importToInternalStorage(context, uri, displayName)?.let { return it }
        }

        return PersistedSound(uri = uri.toString(), displayName = displayName)
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
        if (uri.scheme != "content") {
            return
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun shouldImportToInternalStorage(uri: Uri): Boolean {
        if (uri.scheme != "content") {
            return false
        }
        if (isMediaStoreRingtoneUri(uri)) {
            return false
        }
        return true
    }

    private fun isMediaStoreRingtoneUri(uri: Uri): Boolean {
        return uri.authority?.startsWith("media") == true &&
            (uri.path?.contains("/audio/") == true || uri.path?.contains("/ringtones/") == true)
    }

    private fun importToInternalStorage(
        context: Context,
        sourceUri: Uri,
        displayName: String
    ): PersistedSound? {
        val resolver = context.contentResolver
        val extension = guessExtension(resolver, sourceUri, displayName)
        val dir = File(context.filesDir, "alarm_sounds").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}$extension")
        return try {
            resolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            PersistedSound(
                uri = Uri.fromFile(dest).toString(),
                displayName = displayName
            )
        } catch (_: Exception) {
            dest.delete()
            null
        }
    }

    private fun guessExtension(resolver: android.content.ContentResolver, uri: Uri, displayName: String): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }
        if (fromName != null) {
            return ".$fromName"
        }
        val mime = resolver.getType(uri)
        val fromMime = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!fromMime.isNullOrBlank()) {
            return ".$fromMime"
        }
        return ".mp3"
    }

    private fun isContentUriReadable(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun queryMediaStoreTitle(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") {
            return null
        }
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                if (titleIndex >= 0) {
                    cursor.getString(titleIndex)?.takeIf { isMeaningfulTitle(it) }?.let { return@use it }
                }
                val displayIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                if (displayIndex >= 0) {
                    cursor.getString(displayIndex)?.takeIf { isMeaningfulTitle(it) }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryOpenableDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") {
            return null
        }
        return try {
            context.contentResolver.query(
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
                cursor.getString(index)?.takeIf { isMeaningfulTitle(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** RingtoneManager sometimes returns the MediaStore row id when the title is unavailable. */
    private fun isMeaningfulTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) {
            return false
        }
        if (title.all { it.isDigit() }) {
            return false
        }
        return true
    }
}
