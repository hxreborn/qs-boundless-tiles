package eu.hxreborn.qsboundlesstiles.util

import android.util.Log
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import io.github.libxposed.api.XposedModule

object Logger {
    private const val TAG = "QSBoundlessTiles"

    @Volatile
    private var module: XposedModule? = null

    fun init(module: XposedModule) {
        this.module = module
    }

    fun log(
        msg: String,
        t: Throwable? = null,
    ) {
        module?.apply { if (t != null) log(msg, t) else log(msg) }
        if (t != null) Log.e(TAG, msg, t) else Log.d(TAG, msg)
    }

    inline fun logDebug(msg: () -> String) {
        if (!PrefsManager.debugLogs) return
        log(msg())
    }
}

fun log(
    msg: String,
    t: Throwable? = null,
) = Logger.log(msg, t)

inline fun logDebug(msg: () -> String) = Logger.logDebug(msg)
