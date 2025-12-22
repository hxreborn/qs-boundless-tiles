package eu.hxreborn.qsboundlesstiles.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log

object TileScanner {
    private const val TAG = "QSBoundlessTiles"
    private val SYSTEM_PREFIXES = listOf("com.android.", "com.google.", "android.")

    // Returns 0 on Android 14+ due to SecurityException (sysui_qs_tiles restricted to SDK 33)
    fun getActiveQsTileCount(context: Context): Int =
        runCatching {
            val tileSpec = Settings.Secure.getString(context.contentResolver, "sysui_qs_tiles")
                ?: return@runCatching 0
            tileSpec.split(",").count { it.startsWith("custom(") }
        }.getOrDefault(0)

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
