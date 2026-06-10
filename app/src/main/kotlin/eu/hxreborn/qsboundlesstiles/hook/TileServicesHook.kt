package eu.hxreborn.qsboundlesstiles.hook

import android.os.Build
import android.os.SystemClock
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
    @Volatile private var dbgTsmServiceField: Field? = null

    @Volatile private var dbgTsmComponentField: Field? = null

    // Maps component string → tap nanos for in-flight cold starts.
    private val dbgColdClickNanos = HashMap<String, Long>()

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
            classLoader.loadOrNull(TSM_CLASS)?.also { tsmClass ->
                dbgTsmServiceField = tsmClass.accessibleFieldOrNull("mService")
                dbgTsmComponentField = tsmClass.accessibleFieldOrNull("mComponent")
                hookTileServiceManager(module, tsmClass)
            }
            logDebug {
                "debug probes tsmService=${dbgTsmServiceField != null} tsmComponent=${dbgTsmComponentField != null}"
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
                    chain.proceed()
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
    private fun hookTileServiceManager(
        module: XposedInterface,
        tsmClass: Class<*>,
    ) {
        // Log warm/cold at tap time.
        tsmClass.declaredMethods
            .find { it.name == "handleClick" && it.parameterCount == 1 }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    val tsm = chain.thisObject ?: return@intercept chain.proceed()
                    val warm =
                        dbgTsmServiceField?.let { runCatching { it.get(tsm) }.getOrNull() } != null
                    val comp = dbgTsmComponentField?.let { runCatching { it.get(tsm) }.getOrNull() }
                    if (warm) {
                        logDebug { "tile tap warm component=$comp" }
                    } else {
                        dbgColdClickNanos[comp.toString()] = SystemClock.elapsedRealtimeNanos()
                        logDebug { "tile tap cold component=$comp" }
                    }
                    chain.proceed()
                }
            } ?: logDebug { "debug method missing name=handleClick class=$TSM_CLASS" }

        // Log elapsed time when a cold tile finishes binding.
        tsmClass.declaredMethods
            .find { it.name == "onServiceConnected" && it.parameterCount == 2 }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val tsm = chain.thisObject ?: return@intercept result
                    val comp = dbgTsmComponentField?.let { runCatching { it.get(tsm) }.getOrNull() }
                    val t0 = dbgColdClickNanos.remove(comp.toString())
                    if (t0 != null) {
                        val elapsed = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L
                        logDebug { "tile cold start done elapsed=${elapsed}ms component=$comp" }
                    }
                    result
                }
            } ?: logDebug { "debug method missing name=onServiceConnected class=$TSM_CLASS" }
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
