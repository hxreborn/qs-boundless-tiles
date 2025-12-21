package eu.hxreborn.qsboundlesstiles.prefs

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import eu.hxreborn.qsboundlesstiles.hook.TileServicesHook

object PrefsManager {
    private const val TAG = "QSBoundlessTiles"

    private const val AUTHORITY = "eu.hxreborn.qsboundlesstiles.prefs"
    private val MASTER_ENABLED_URI = Uri.parse("content://$AUTHORITY/master_enabled")
    private val MAX_BOUND_URI = Uri.parse("content://$AUTHORITY/max_bound")
    private val PROVIDER_URI = Uri.parse("content://$AUTHORITY")

    private var hookBinder: HookBinder? = null

    private var masterEnabledCache: Boolean = true
    private var maxBoundCache: Int = AppPrefsHelper.DEFAULT_MAX_BOUND
    private var lastCacheUpdate: Long = 0
    private const val CACHE_TTL_MS = 5000L

    fun init() {
        Log.d(TAG, "PrefsManager.init() called")
        hookBinder = HookBinder()
        refreshCache()
        Log.d(TAG, "PrefsManager.init() done, master=$masterEnabledCache, maxBound=$maxBoundCache")
    }

    @Suppress("PrivateApi")
    private fun getSystemContext(): Context? =
        runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val method = activityThreadClass.getMethod("currentApplication")
            method.invoke(null) as? Context
        }.onFailure {
            Log.e(TAG, "Failed to get context via ActivityThread", it)
        }.getOrNull()

    private fun refreshCache() {
        Log.d(TAG, "refreshCache() querying ContentProvider...")

        val context =
            getSystemContext() ?: run {
                Log.w(TAG, "refreshCache() context is null, cannot query provider")
                return
            }

        runCatching {
            Log.d(TAG, "refreshCache() got context: ${context.packageName}")

            context.contentResolver.query(MASTER_ENABLED_URI, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) masterEnabledCache = c.getInt(0) == 1
            }

            context.contentResolver.query(MAX_BOUND_URI, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) maxBoundCache = c.getInt(0)
            }

            lastCacheUpdate = System.currentTimeMillis()
            Log.d(
                TAG,
                "refreshCache() success: master=$masterEnabledCache, maxBound=$maxBoundCache",
            )
        }.onFailure {
            Log.e(TAG, "refreshCache() failed to query provider", it)
        }
    }

    private fun ensureCacheFresh() {
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_TTL_MS) {
            refreshCache()
        }
    }

    fun isMasterEnabled(): Boolean {
        ensureCacheFresh()
        return masterEnabledCache
    }

    fun getMaxBound(): Int {
        ensureCacheFresh()
        return maxBoundCache
    }

    fun getEffectiveMaxBound(): Int {
        ensureCacheFresh()
        Log.d(TAG, "getEffectiveMaxBound() = $maxBoundCache")
        return maxBoundCache
    }

    private fun countActiveQsTiles(): Int =
        runCatching {
            val context = getSystemContext() ?: return@runCatching 0
            val tileSpec =
                Settings.Secure.getString(context.contentResolver, "sysui_qs_tiles")
                    ?: return@runCatching 0

            tileSpec.split(",").count { it.startsWith("custom(") }.also {
                Log.d(TAG, "countActiveQsTiles() = $it")
            }
        }.onFailure {
            Log.e(TAG, "countActiveQsTiles() failed", it)
        }.getOrDefault(0)

    fun passBinder() {
        val context =
            getSystemContext() ?: run {
                Log.w(TAG, "passBinder() context is null")
                return
            }

        val binder =
            hookBinder ?: run {
                Log.w(TAG, "passBinder() hookBinder is null")
                return
            }

        runCatching {
            val boundLimit = TileServicesHook.activeMaxBound
            val qsCount = countActiveQsTiles()

            val extras =
                Bundle().apply {
                    putBinder(PrefsProvider.ARG_BINDER, binder)
                    putLong(PrefsProvider.ARG_TIMESTAMP, System.currentTimeMillis())
                    putInt(PrefsProvider.ARG_BOUND_LIMIT, boundLimit)
                    putInt(PrefsProvider.ARG_QS_COUNT, qsCount)
                }

            val result =
                context.contentResolver.call(
                    PROVIDER_URI,
                    PrefsProvider.METHOD_LINK_BINDER,
                    null,
                    extras,
                )

            val success = result?.getBoolean("success", false) ?: false
            Log.d(TAG, "passBinder() success=$success, boundLimit=$boundLimit, qsCount=$qsCount")
        }.onFailure {
            Log.e(TAG, "passBinder() failed", it)
        }
    }
}
