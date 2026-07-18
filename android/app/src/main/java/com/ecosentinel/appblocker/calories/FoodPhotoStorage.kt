package com.ecosentinel.appblocker.calories

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object FoodPhotoStorage {

    private const val DIR = "food_photos"

    fun createPhotoFile(context: Context): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }

    fun importFromUri(context: Context, uri: Uri): String? {
        val dest = createPhotoFile(context)
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (!copied) {
            dest.delete()
            return null
        }
        return relativePath(context, dest)
    }

    fun resolveFile(context: Context, photoPath: String): File? {
        if (photoPath.isBlank()) return null
        val file = File(context.filesDir, photoPath)
        return file.takeIf { it.exists() }
    }

    fun fileProviderUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun relativePath(context: Context, file: File): String {
        return file.relativeTo(context.filesDir).path.replace('\\', '/')
    }

    fun deletePhoto(context: Context, photoPath: String) {
        resolveFile(context, photoPath)?.delete()
    }
}
