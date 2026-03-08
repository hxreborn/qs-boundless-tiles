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
        when (method) {
            METHOD_WRITE_HOOK_STATUS -> {
                val status = arg?.toIntOrNull() ?: return null
                prefs.edit(commit = true) { putInt(KEY_HOOK_STATUS, status) }
            }

            METHOD_RECORD_TILE_EVENT -> {
                val event = arg ?: return null
                appendEvent(KEY_TILE_EVENTS, event, MAX_TILE_EVENTS)
            }

            METHOD_CLEAR_EVENTS -> {
                prefs.edit(commit = true) {
                    putString(KEY_TILE_EVENTS, "")
                }
            }

        }
        return null
    }

    private fun appendEvent(
        key: String,
        event: String,
        maxEvents: Int,
    ) {
        val existing = prefs.getString(key, "") ?: ""
        val lines = existing.lines().filter { it.isNotBlank() }.takeLast(maxEvents - 1)
        val updated = (lines + event).joinToString("\n")
        prefs.edit(commit = true) { putString(key, updated) }
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
        const val KEY_TILE_EVENTS = "tile_events"

        const val METHOD_WRITE_HOOK_STATUS = "writeHookStatus"

        const val METHOD_RECORD_TILE_EVENT = "recordTileEvent"
        const val METHOD_CLEAR_EVENTS = "clearEvents"

        private const val MAX_TILE_EVENTS = 500

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
    }
}
