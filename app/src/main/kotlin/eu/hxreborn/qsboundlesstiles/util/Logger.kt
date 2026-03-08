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
        if (t == null) {
            module?.log(msg)
            Log.d(TAG, msg)
        } else {
            module?.log(msg, t)
            Log.e(TAG, msg, t)
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
): Unit = Logger.log(msg, t)

inline fun logDebug(msg: () -> String): Unit = Logger.logDebug(msg)
