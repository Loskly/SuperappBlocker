package com.ecosentinel.appblocker.survival

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * A ContentProvider whose sole purpose is to guarantee that
 * SurvivalManager.runCheck() is called whenever the app process starts,
 * regardless of which component triggered the process creation.
 *
 * ContentProvider.onCreate() runs before Application.onCreate(),
 * ensuring the earliest possible recovery trigger.
 */
class SurvivalContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return true
        Log.i(TAG, "SurvivalContentProvider.onCreate — scheduling survival check")
        SurvivalManager.runCheck(ctx.applicationContext, SurvivalManager.REASON_CONTENT_PROVIDER)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?,
        selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "SurvivalContentProvider"
    }
}
