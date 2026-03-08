package eu.hxreborn.qsboundlesstiles.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.ResolveInfo
import android.service.quicksettings.TileService

data class TileProviderInfo(
    val appName: String,
    val tileCount: Int,
)

object TileScanner {
    private val SYSTEM_PREFIXES = listOf("com.android.", "com.google.", "android.")

    fun getThirdPartyTileProviders(context: Context): List<TileProviderInfo> =
        runCatching {
            val pm = context.packageManager
            queryThirdPartyServices(context)
                .groupBy { it.serviceInfo.packageName }
                .map { (pkg, services) ->
                    val appName =
                        runCatching {
                            pm
                                .getApplicationInfo(pkg, ApplicationInfoFlags.of(0))
                                .loadLabel(pm)
                                .toString()
                        }.getOrDefault(pkg)
                    TileProviderInfo(appName = appName, tileCount = services.size)
                }.sortedByDescending { it.tileCount }
        }.getOrDefault(emptyList())

    private fun queryThirdPartyServices(context: Context): List<ResolveInfo> =
        context.packageManager
            .queryIntentServices(Intent(TileService.ACTION_QS_TILE), PackageManager.MATCH_ALL)
            .filter { it.serviceInfo?.packageName?.let { pkg -> !isSystemPackage(pkg) } ?: false }

    private fun isSystemPackage(packageName: String): Boolean =
        SYSTEM_PREFIXES.any { packageName.startsWith(it) }
}
