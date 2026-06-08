package eu.hxreborn.qsboundlesstiles.hook

import android.os.Build
import android.os.SystemClock
import android.util.ArrayMap
import eu.hxreborn.qsboundlesstiles.BuildConfig
import eu.hxreborn.qsboundlesstiles.log
import eu.hxreborn.qsboundlesstiles.logDebug
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TILE_SERVICES_CLASS = "com.android.systemui.qs.external.TileServices"
private const val TSM_CLASS = "com.android.systemui.qs.external.TileServiceManager"

object TileServicesHook {
    @Volatile private var maxBoundField: Field? = null

    @Volatile private var recalculateMethod: Method? = null

    @Volatile var tileServicesInstance: Any? = null
        private set

    // Set once at hook() setup. Dead fields in release; R8 folds the BuildConfig.DEBUG branches.
    @Volatile private var dbgServicesField: Field? = null

    @Volatile private var dbgTsmServiceField: Field? = null

    @Volatile private var dbgTsmBoundField: Field? = null

    @Volatile private var dbgTsmComponentField: Field? = null

    @Volatile private var dbgLastRecalcNanos = 0L

    fun hook(
        module: XposedInterface,
        classLoader: ClassLoader,
    ) {
        log("hook start api=${Build.VERSION.SDK_INT}")

        val tileServicesClass =
            classLoader.loadOrNull(TILE_SERVICES_CLASS) ?: run {
                log("class missing name=$TILE_SERVICES_CLASS")
                return
            }

        maxBoundField = tileServicesClass.accessibleFieldOrNull("mMaxBound")
        if (maxBoundField == null) {
            log("field missing name=mMaxBound")
            return
        }

        if (BuildConfig.DEBUG) {
            dbgServicesField = tileServicesClass.accessibleFieldOrNull("mServices")
            classLoader.loadOrNull(TSM_CLASS)?.also { tsmClass ->
                dbgTsmServiceField = tsmClass.accessibleFieldOrNull("mService")
                dbgTsmBoundField = tsmClass.accessibleFieldOrNull("mBound")
                dbgTsmComponentField = tsmClass.accessibleFieldOrNull("mComponent")
                hookTileServiceManagerForTiming(module, tsmClass)
            }
            logDebug {
                "debug probes" +
                    " mServices=${dbgServicesField != null}" +
                    " tsmService=${dbgTsmServiceField != null}" +
                    " tsmBound=${dbgTsmBoundField != null}" +
                    " tsmComponent=${dbgTsmComponentField != null}"
            }
        }

        tileServicesClass.declaredConstructors.forEach { constructor ->
            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                val ts = chain.thisObject ?: return@intercept result
                tileServicesInstance = ts
                applyUserMaxBound(ts)
                log("hooked constructor maxBound=$maxBound")
                result
            }
        }

        tileServicesClass.declaredMethods
            .find { it.name == "setMemoryPressure" && it.parameterCount == 1 }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    chain.thisObject?.let { ts ->
                        applyUserMaxBound(ts)
                        logDebug { "setMemoryPressure restored maxBound=$maxBound" }
                    }
                    result
                }
            } ?: log("method missing name=setMemoryPressure")

        tileServicesClass.declaredMethods
            .find { it.name == "recalculateBindAllowance" && it.parameterCount == 0 }
            ?.also { recalculateMethod = it.apply { isAccessible = true } }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    chain.thisObject?.let { ts -> applyUserMaxBound(ts) }
                    if (BuildConfig.DEBUG) {
                        dbgLastRecalcNanos = SystemClock.elapsedRealtimeNanos()
                        val result = chain.proceed()
                        val elapsed =
                            (SystemClock.elapsedRealtimeNanos() - dbgLastRecalcNanos) / 1_000_000L
                        chain.thisObject?.let { ts -> logTileStates(ts, elapsed) }
                        result
                    } else {
                        chain.proceed()
                    }
                }
            } ?: log("method missing name=recalculateBindAllowance")

        onMaxBoundChangedHandlers.add { newValue ->
            tileServicesInstance?.let { instance ->
                setMaxBound(instance, newValue)
                runCatching { recalculateMethod?.invoke(instance) }
                log("live updated maxBound=$newValue")
            }
        }

        log("hook done")
    }

    // No internal BuildConfig.DEBUG guard; only reachable from the debug branch in hook().
    private fun hookTileServiceManagerForTiming(
        module: XposedInterface,
        tsmClass: Class<*>,
    ) {
        tsmClass.declaredMethods
            .find { it.name == "onServiceConnected" && it.parameterCount == 2 }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val tsm = chain.thisObject ?: return@intercept result
                    val elapsedMs =
                        (SystemClock.elapsedRealtimeNanos() - dbgLastRecalcNanos) / 1_000_000L
                    val comp = dbgTsmComponentField?.let { runCatching { it.get(tsm) }.getOrNull() }
                    logDebug { "tile bound elapsed=${elapsedMs}ms component=$comp" }
                    result
                }
            } ?: logDebug { "debug method missing name=onServiceConnected class=$TSM_CLASS" }
    }

    // No internal BuildConfig.DEBUG guard; only reachable from the debug branch in recalculateBindAllowance.
    private fun logTileStates(
        tileServices: Any,
        elapsedMs: Long,
    ) {
        val sf = dbgServicesField
        if (sf == null) {
            logDebug { "recalculate done maxBound=$maxBound elapsed=${elapsedMs}ms" }
            return
        }

        @Suppress("UNCHECKED_CAST")
        val services = runCatching { sf.get(tileServices) as? ArrayMap<*, *> }.getOrNull()
        if (services == null || services.isEmpty()) {
            logDebug { "recalculate done maxBound=$maxBound elapsed=${elapsedMs}ms" }
            return
        }

        val svf = dbgTsmServiceField
        val bf = dbgTsmBoundField
        var warm = 0
        var binding = 0
        var cold = 0
        for (tsm in services.values) {
            tsm ?: continue
            when {
                svf != null && runCatching { svf.get(tsm) }.getOrNull() != null -> warm++
                bf != null && runCatching { bf.getBoolean(tsm) }.getOrDefault(false) -> binding++
                else -> cold++
            }
        }

        logDebug {
            "recalculate done warm=$warm binding=$binding cold=$cold maxBound=$maxBound elapsed=${elapsedMs}ms"
        }
    }

    private fun setMaxBound(
        tileServices: Any,
        value: Int,
    ) {
        runCatching {
            maxBoundField?.setInt(tileServices, value)
        }.onFailure { log("set maxBound failed", it) }
    }

    private fun applyUserMaxBound(tileServices: Any) {
        setMaxBound(tileServices, maxBound.coerceAtLeast(Prefs.maxBound.default))
    }
}
