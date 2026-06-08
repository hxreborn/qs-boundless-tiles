package eu.hxreborn.qsboundlesstiles

import android.util.Log

private const val TAG = "WarmTiles"

fun log(
    msg: String,
    t: Throwable? = null,
) {
    val m = runCatching { module }.getOrNull() ?: return
    if (t != null) {
        m.log(Log.ERROR, TAG, msg, t)
    } else {
        m.log(Log.INFO, TAG, msg)
    }
}

inline fun logDebug(msg: () -> String) {
    if (BuildConfig.DEBUG) log(msg())
}
