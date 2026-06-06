package eu.hxreborn.qsboundlesstiles.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

data class AppPrefs(
    val maxBound: Int,
)

class PrefsRepository(
    private val localPrefs: SharedPreferences,
    private val remotePrefsProvider: () -> SharedPreferences?,
) {
    fun <T : Any> read(spec: PrefSpec<T>): T = spec.read(localPrefs)

    fun <T : Any> save(
        pref: PrefSpec<T>,
        value: T,
    ) {
        localPrefs.edit { pref.write(this, value) }
        remotePrefsProvider()?.edit(commit = true) { pref.write(this, value) }
    }

    val state: Flow<AppPrefs> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    trySend(readAll())
                }
            localPrefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { localPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }.onStart { emit(readAll()) }
            .distinctUntilChanged()

    private fun readAll() =
        AppPrefs(
            maxBound = Prefs.maxBound.read(localPrefs),
        )
}
