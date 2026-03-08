package eu.hxreborn.qsboundlesstiles.hook

import android.os.Build
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import eu.hxreborn.qsboundlesstiles.ui.EventType
import eu.hxreborn.qsboundlesstiles.util.log
import eu.hxreborn.qsboundlesstiles.util.logDebug
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TILE_SERVICES_CLASS = "com.android.systemui.qs.external.TileServices"

object TileServicesHook {
    const val HOOK_CONSTRUCTOR = 1
    const val HOOK_SET_MEMORY_PRESSURE = 2
    const val HOOK_RECALCULATE_BIND_ALLOWANCE = 4
    const val HOOK_HANDLE_CLICK = 8
    const val HOOK_ON_SERVICE_CONNECTED = 16
    const val HOOK_SERVICE_DIED = 32
    const val HOOK_ALL =
        HOOK_CONSTRUCTOR or HOOK_SET_MEMORY_PRESSURE or HOOK_RECALCULATE_BIND_ALLOWANCE or
            HOOK_HANDLE_CLICK or HOOK_ON_SERVICE_CONNECTED or HOOK_SERVICE_DIED

    @Volatile
    private var maxBoundField: Field? = null

    @Volatile
    private var recalculateMethod: Method? = null

    @Volatile
    var tileServicesInstance: Any? = null
        internal set

    fun hook(
        module: XposedModule,
        classLoader: ClassLoader,
    ) {
        log(
            "Hooking on ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )

        val tileServicesClass =
            runCatching {
                classLoader.loadClass(TILE_SERVICES_CLASS)
            }.onFailure {
                log("TileServices class not found, aborting", it)
            }.getOrNull() ?: run {
                PrefsManager.setHookStatus(0)
                return
            }

        maxBoundField =
            runCatching {
                tileServicesClass.getDeclaredField("mMaxBound").apply { isAccessible = true }
            }.onFailure {
                log("mMaxBound field not found, aborting", it)
            }.getOrNull()

        if (maxBoundField == null) {
            PrefsManager.setHookStatus(0)
            return
        }

        var hookStatus = 0

        tileServicesClass.declaredConstructors.forEach { constructor ->
            module.hook(constructor, TileServicesConstructorHooker::class.java)
        }
        hookStatus = hookStatus or HOOK_CONSTRUCTOR

        tileServicesClass.declaredMethods
            .find {
                it.name == "setMemoryPressure" &&
                    it.parameterCount == 1
            }?.let {
                module.hook(it, SetMemoryPressureHooker::class.java)
                hookStatus = hookStatus or HOOK_SET_MEMORY_PRESSURE
            } ?: log("setMemoryPressure not found (removed in Android 15+, not needed)")

        tileServicesClass.declaredMethods
            .find {
                it.name == "recalculateBindAllowance" &&
                    it.parameterCount == 0
            }?.also { recalculateMethod = it.apply { isAccessible = true } }
            ?.let {
                module.hook(it, RecalculateBindAllowanceHooker::class.java)
                hookStatus = hookStatus or HOOK_RECALCULATE_BIND_ALLOWANCE
            } ?: log("recalculateBindAllowance not found -- live binding updates unavailable")

        hookStatus = hookStatus or TileActivityHook.hook(module, classLoader)

        PrefsManager.setHookStatus(hookStatus)

        PrefsManager.onMaxBoundChanged = { newValue ->
            tileServicesInstance?.let { instance ->
                setMaxBound(instance, newValue)
                runCatching { recalculateMethod?.invoke(instance) }
                log("Live updated mMaxBound=$newValue")
                PrefsManager.recordTileEvent(EventType.LIMIT_SET, null, null, "mMaxBound=$newValue")
            }
        }

        log("Hooked TileServices (status=0b${hookStatus.toString(2).padStart(6, '0')})")
    }

    fun extractContext(tileServices: Any) {
        val context =
            generateSequence<Class<*>>(tileServices.javaClass) { it.superclass }
                .take(20)
                .firstNotNullOfOrNull { cls ->
                    runCatching {
                        cls.getDeclaredField("mContext").apply { isAccessible = true }
                    }.getOrNull()?.let { field ->
                        field.get(tileServices) as? android.content.Context
                    }
                }

        if (context != null) {
            TileActivityHook.setContext(context)
            return
        }

        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val app =
                activityThread.getMethod("currentApplication").invoke(null)
                    as? android.content.Context
            app?.let { TileActivityHook.setContext(it) }
        }.onFailure {
            log("Failed to extract SystemUI context", it)
        }
    }

    fun setMaxBound(
        tileServices: Any,
        value: Int,
    ) {
        runCatching {
            maxBoundField?.setInt(tileServices, value)
        }.onFailure {
            log("Failed to set mMaxBound", it)
        }
    }

    fun applyUserMaxBound(tileServices: Any) {
        val newMax = PrefsManager.maxBound.coerceAtLeast(Prefs.maxBound.default)
        setMaxBound(tileServices, newMax)
    }
}

@XposedHooker
class TileServicesConstructorHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val tileServices = callback.thisObject ?: return
            TileServicesHook.tileServicesInstance = tileServices
            TileServicesHook.extractContext(tileServices)
            TileServicesHook.applyUserMaxBound(tileServices)
            log("TileServices constructed, mMaxBound=${PrefsManager.maxBound}")
            PrefsManager.flushHookStatus()
            PrefsManager.recordTileEvent(
                EventType.LIMIT_SET,
                null,
                null,
                "mMaxBound=${PrefsManager.maxBound}",
            )
        }
    }
}

@XposedHooker
class SetMemoryPressureHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            callback.thisObject?.let {
                TileServicesHook.applyUserMaxBound(it)
                val memoryPressure = callback.args?.getOrNull(0) as? Boolean ?: return
                logDebug {
                    "setMemoryPressure($memoryPressure): " +
                        "restored mMaxBound=${PrefsManager.maxBound}"
                }
                if (memoryPressure) {
                    PrefsManager.recordTileEvent(
                        EventType.MEM_PRESSURE,
                        null,
                        null,
                        "Memory pressure intercepted, limit preserved at ${PrefsManager.maxBound}",
                    )
                }
            }
        }
    }
}

@XposedHooker
class RecalculateBindAllowanceHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun before(callback: BeforeHookCallback) {
            callback.thisObject?.let {
                TileServicesHook.applyUserMaxBound(it)
                logDebug { "recalculateBindAllowance: set mMaxBound=${PrefsManager.maxBound}" }
            }
        }

        @JvmStatic
        @AfterInvocation
        fun after(
            @Suppress("UNUSED_PARAMETER") callback: AfterHookCallback,
        ) = Unit
    }
}
