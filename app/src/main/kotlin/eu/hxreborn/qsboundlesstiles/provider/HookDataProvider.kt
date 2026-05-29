package eu.hxreborn.qsboundlesstiles.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.core.content.edit

class HookDataProvider : ContentProvider() {
    private val prefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        if (method == METHOD_WRITE_HOOK_STATUS) {
            arg?.toIntOrNull()?.let { status ->
                prefs.edit(commit = true) { putInt(KEY_HOOK_STATUS, status) }
            }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "eu.hxreborn.qsboundlesstiles.hookdata"
        const val PREFS_NAME = "hook_data"
        const val KEY_HOOK_STATUS = "hook_status"

        const val METHOD_WRITE_HOOK_STATUS = "writeHookStatus"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
    }
}
