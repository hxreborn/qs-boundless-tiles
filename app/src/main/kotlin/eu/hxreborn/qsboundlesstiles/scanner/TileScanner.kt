package eu.hxreborn.qsboundlesstiles.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.TileService
import android.util.Log
import eu.hxreborn.qsboundlesstiles.prefs.AppPrefsHelper

object TileScanner {
    private const val TAG = "QSBoundlessTiles"

    private val SYSTEM_PREFIXES = listOf("com.android.", "com.google.", "android.")

    data class TileInfo(
        val activeInQs: Int,
        val availableApps: Int,
    )

    fun getTileInfo(context: Context): TileInfo {
        val activeCount = getActiveQsTileCount(context)
        val availableCount = getThirdPartyTileCount(context)
        return TileInfo(activeInQs = activeCount, availableApps = availableCount)
    }

    fun getActiveQsTileCount(context: Context): Int =
        AppPrefsHelper.getActiveQsCount(context).also {
            Log.d(TAG, "Active QS tiles (from prefs): $it")
        }

    fun getThirdPartyTileCount(context: Context): Int = getThirdPartyTilePackages(context).size

    fun getThirdPartyTilePackages(context: Context): Set<String> =
        runCatching {
            val intent = Intent(TileService.ACTION_QS_TILE)
            context.packageManager
                .queryIntentServices(intent, PackageManager.MATCH_ALL)
                .mapNotNull { it.serviceInfo?.packageName }
                .filterNot(::isSystemPackage)
                .toSet()
                .also { Log.d(TAG, "TileScanner found ${it.size} third-party tiles: $it") }
        }.onFailure {
            Log.e(TAG, "TileScanner failed to query tiles", it)
        }.getOrDefault(emptySet())

    private fun isSystemPackage(packageName: String): Boolean =
        SYSTEM_PREFIXES.any { packageName.startsWith(it) }
}
