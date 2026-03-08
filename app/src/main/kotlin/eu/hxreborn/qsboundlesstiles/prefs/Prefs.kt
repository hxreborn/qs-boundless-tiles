package eu.hxreborn.qsboundlesstiles.prefs

object Prefs {
    const val GROUP = "settings"

    val maxBound = IntPref("max_bound", 3, 3..30)
    val debugLogs = BoolPref("debug_logs", false)
}
