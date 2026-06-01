package eu.hxreborn.qsboundlesstiles

import android.util.Log
import io.github.libxposed.api.XposedModule

object Logger {
    private const val TAG = "WarmTiles"

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
        } else {
            module?.log(Log.INFO, TAG, msg)
        }
    }

    inline fun logDebug(msg: () -> String) {
        log(msg())
    }
}

fun log(
    msg: String,
    t: Throwable? = null,
) = Logger.log(msg, t)

inline fun logDebug(msg: () -> String) = Logger.logDebug(msg)
