package eu.hxreborn.qsboundlesstiles.prefs

import android.content.SharedPreferences
import androidx.core.content.edit
import eu.hxreborn.qsboundlesstiles.ui.PrefsState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface PrefsRepository {
    val state: Flow<PrefsState>

    fun <T : Any> save(
        pref: PrefSpec<T>,
        value: T,
    )
}

class PrefsRepositoryImpl(
    private val localPrefs: SharedPreferences,
    private val remotePrefsProvider: () -> SharedPreferences?,
) : PrefsRepository {
    override val state: Flow<PrefsState> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    trySend(localPrefs.toPrefsState())
                }
            trySend(localPrefs.toPrefsState())
            localPrefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { localPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    override fun <T : Any> save(
        pref: PrefSpec<T>,
        value: T,
    ) {
        localPrefs.edit { pref.write(this, value) }
        remotePrefsProvider()?.edit(commit = true) { pref.write(this, value) }
    }

    private fun SharedPreferences.toPrefsState() =
        PrefsState(
            maxBound = Prefs.maxBound.read(this),
            debugLogs = Prefs.debugLogs.read(this),
        )
}
