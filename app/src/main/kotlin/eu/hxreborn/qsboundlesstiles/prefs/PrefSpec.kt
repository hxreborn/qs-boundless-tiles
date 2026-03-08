package eu.hxreborn.qsboundlesstiles.prefs

import android.content.SharedPreferences

sealed class PrefSpec<T : Any>(
    val key: String,
    val default: T,
) {
    abstract fun read(prefs: SharedPreferences): T

    abstract fun write(
        editor: SharedPreferences.Editor,
        value: T,
    )
}

class BoolPref(
    key: String,
    default: Boolean,
) : PrefSpec<Boolean>(key, default) {
    override fun read(prefs: SharedPreferences): Boolean = prefs.getBoolean(key, default)

    override fun write(
        editor: SharedPreferences.Editor,
        value: Boolean,
    ) {
        editor.putBoolean(key, value)
    }
}

class IntPref(
    key: String,
    default: Int,
    val range: IntRange? = null,
) : PrefSpec<Int>(key, default) {
    override fun read(prefs: SharedPreferences): Int {
        val raw = prefs.getInt(key, default)
        return range?.let { raw.coerceIn(it) } ?: raw
    }

    override fun write(
        editor: SharedPreferences.Editor,
        value: Int,
    ) {
        editor.putInt(key, value)
    }
}
