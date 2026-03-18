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
        if (t != null) {
            module?.log(Log.ERROR, TAG, msg, t)
            Log.e(TAG, msg, t)
        } else {
            module?.log(Log.INFO, TAG, msg)
            Log.d(TAG, msg)
        }
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
