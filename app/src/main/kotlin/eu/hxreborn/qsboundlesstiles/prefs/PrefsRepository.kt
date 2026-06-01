package eu.hxreborn.qsboundlesstiles.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import eu.hxreborn.qsboundlesstiles.ui.PrefsState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PrefsRepository(
    private val localPrefs: SharedPreferences,
    private val remotePrefsProvider: () -> SharedPreferences?,
) {
    val state: Flow<PrefsState> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    trySend(localPrefs.toPrefsState())
                }
            trySend(localPrefs.toPrefsState())
            localPrefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { localPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    fun <T : Any> save(
        pref: PrefSpec<T>,
        value: T,
    ) {
        localPrefs.edit { pref.write(this, value) }
        remotePrefsProvider()?.edit(commit = true) { pref.write(this, value) }
    }

    private fun SharedPreferences.toPrefsState() =
        PrefsState(
            maxBound = Prefs.maxBound.read(this),
        )
}
