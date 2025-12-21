package eu.hxreborn.qsboundlesstiles.prefs

import android.content.Context
import android.content.SharedPreferences

object AppPrefsHelper {
    private const val PREFS_NAME = "settings"

    private const val KEY_MASTER_ENABLED = "master_enabled"
    private const val KEY_MAX_BOUND = "max_bound"
    private const val KEY_MAX_BOUND_SET = "max_bound_set"
    private const val KEY_HOOK_TIMESTAMP = "hook_timestamp"
    private const val KEY_HOOK_BOUND_LIMIT = "hook_bound_limit"
    private const val KEY_AUTO_BUFFER = "auto_buffer"
    private const val KEY_ACTIVE_QS_COUNT = "active_qs_count"

    const val DEFAULT_MAX_BOUND = 15
    const val DEFAULT_AUTO_BUFFER = 2

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private inline fun Context.editPrefs(block: SharedPreferences.Editor.() -> Unit) {
        getPrefs(this).edit().apply(block).commit()
    }

    fun isMasterEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_MASTER_ENABLED, true)

    fun setMasterEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.editPrefs { putBoolean(KEY_MASTER_ENABLED, enabled) }
    }

    fun getMaxBound(context: Context): Int =
        getPrefs(context).getInt(KEY_MAX_BOUND, DEFAULT_MAX_BOUND)

    fun setMaxBound(
        context: Context,
        value: Int,
    ) {
        context.editPrefs {
            putInt(KEY_MAX_BOUND, value.coerceIn(3, 30))
            putBoolean(KEY_MAX_BOUND_SET, true)
        }
    }

    fun isMaxBoundSet(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_MAX_BOUND_SET, false)

    fun getHookTimestamp(context: Context): Long = getPrefs(context).getLong(KEY_HOOK_TIMESTAMP, 0L)

    fun setHookTimestamp(
        context: Context,
        timestamp: Long,
    ) {
        context.editPrefs { putLong(KEY_HOOK_TIMESTAMP, timestamp) }
    }

    fun getHookBoundLimit(context: Context): Int {
        if (!isHookAlive(context)) return 3
        val stored = getPrefs(context).getInt(KEY_HOOK_BOUND_LIMIT, 0)
        return if (stored == 0) getMaxBound(context) else stored
    }

    fun setHookBoundLimit(
        context: Context,
        limit: Int,
    ) {
        context.editPrefs { putInt(KEY_HOOK_BOUND_LIMIT, limit) }
    }

    fun getAutoBuffer(context: Context): Int =
        getPrefs(context).getInt(KEY_AUTO_BUFFER, DEFAULT_AUTO_BUFFER)

    fun isHookAlive(context: Context): Boolean =
        getHookTimestamp(context) >
            System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()

    fun getActiveQsCount(context: Context): Int = getPrefs(context).getInt(KEY_ACTIVE_QS_COUNT, 0)

    fun setActiveQsCount(
        context: Context,
        count: Int,
    ) {
        context.editPrefs { putInt(KEY_ACTIVE_QS_COUNT, count) }
    }
}
