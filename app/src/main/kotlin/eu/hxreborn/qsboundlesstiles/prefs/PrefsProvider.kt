package eu.hxreborn.qsboundlesstiles.prefs

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

class PrefsProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "eu.hxreborn.qsboundlesstiles.prefs"

        private const val CODE_MASTER_ENABLED = 1
        private const val CODE_MAX_BOUND = 2

        const val METHOD_LINK_BINDER = "link_binder"
        const val ARG_BINDER = "binder"
        const val ARG_TIMESTAMP = "timestamp"
        const val ARG_BOUND_LIMIT = "bound_limit"
        const val ARG_QS_COUNT = "qs_count"
    }

    private val uriMatcher =
        UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "master_enabled", CODE_MASTER_ENABLED)
            addURI(AUTHORITY, "max_bound", CODE_MAX_BOUND)
        }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null

        return when (uriMatcher.match(uri)) {
            CODE_MASTER_ENABLED -> {
                val enabled = AppPrefsHelper.isMasterEnabled(context)
                MatrixCursor(arrayOf("value"))
                    .apply {
                        addRow(arrayOf(if (enabled) 1 else 0))
                    }.also { Log.d("QSBoundlessTiles", "PrefsProvider: master_enabled=$enabled") }
            }

            CODE_MAX_BOUND -> {
                val maxBound = AppPrefsHelper.getMaxBound(context)
                MatrixCursor(arrayOf("value"))
                    .apply {
                        addRow(arrayOf(maxBound))
                    }.also { Log.d("QSBoundlessTiles", "PrefsProvider: max_bound=$maxBound") }
            }

            else -> {
                null
            }
        }
    }

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        val context = context ?: return null

        return when (method) {
            METHOD_LINK_BINDER -> {
                extras?.getBinder(ARG_BINDER)?.let { binder ->
                    val timestamp = extras.getLong(ARG_TIMESTAMP, 0L)
                    val boundLimit = extras.getInt(ARG_BOUND_LIMIT, 0)
                    val qsCount = extras.getInt(ARG_QS_COUNT, 0)

                    HookBinderHolder.link(binder, context, timestamp, boundLimit, qsCount)
                    Log.d(
                        "QSBoundlessTiles",
                        "PrefsProvider: binder linked, boundLimit=$boundLimit, qsCount=$qsCount",
                    )
                }
                Bundle().apply { putBoolean("success", extras?.getBinder(ARG_BINDER) != null) }
            }

            else -> {
                super.call(method, arg, extras)
            }
        }
    }

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
}
