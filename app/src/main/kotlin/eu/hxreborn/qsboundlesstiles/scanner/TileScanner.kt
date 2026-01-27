package eu.hxreborn.qsboundlesstiles.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.TileService

object TileScanner {
    private val SYSTEM_PREFIXES = listOf("com.android.", "com.google.", "android.")

    fun getThirdPartyTileCount(context: Context): Int =
        runCatching {
            val intent = Intent(TileService.ACTION_QS_TILE)
            context.packageManager
                .queryIntentServices(intent, PackageManager.MATCH_ALL)
                .count { it.serviceInfo?.packageName?.let { pkg -> !isSystemPackage(pkg) } == true }
        }.getOrDefault(0)

    private fun isSystemPackage(packageName: String): Boolean =
        SYSTEM_PREFIXES.any { packageName.startsWith(it) }
}
