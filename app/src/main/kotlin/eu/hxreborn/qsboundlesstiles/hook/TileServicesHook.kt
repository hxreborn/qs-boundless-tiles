package eu.hxreborn.qsboundlesstiles.hook

import eu.hxreborn.qsboundlesstiles.QSBoundlessTilesModule.Companion.log
import eu.hxreborn.qsboundlesstiles.module
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field

private const val TILE_SERVICES_CLASS = "com.android.systemui.qs.external.TileServices"
private const val TAG = "[Hook]"
private const val TAG_CTOR = "[Ctor]"
private const val TAG_MEMORY = "[Memory]"
private const val TAG_BIND = "[Bind]"

object TileServicesHook {
    private var maxBoundField: Field? = null

    fun hook(classLoader: ClassLoader) {
        log("$TAG Starting hook...")

        val tileServicesClass = classLoader.loadClass(TILE_SERVICES_CLASS)

        runCatching {
            val defaultMaxBound = tileServicesClass.getDeclaredField("DEFAULT_MAX_BOUND")
            defaultMaxBound.isAccessible = true
            val reducedMaxBound = tileServicesClass.getDeclaredField("REDUCED_MAX_BOUND")
            reducedMaxBound.isAccessible = true
            log(
                "$TAG DEFAULT_MAX_BOUND=${defaultMaxBound.getInt(null)}, " +
                    "REDUCED_MAX_BOUND=${reducedMaxBound.getInt(null)}",
            )
        }.onFailure {
            log("$TAG Could not read constants: ${it.message}")
        }

        maxBoundField =
            runCatching {
                tileServicesClass.getDeclaredField("mMaxBound").apply { isAccessible = true }
            }.onSuccess {
                log("$TAG Found mMaxBound field")
            }.onFailure {
                log("$TAG mMaxBound not found, aborting: ${it.message}")
            }.getOrNull()

        if (maxBoundField == null) return

        tileServicesClass.declaredConstructors.forEach { constructor ->
            module.hook(constructor, TileServicesConstructorHooker::class.java)
            log("$TAG Hooked constructor(${constructor.parameterCount})")
        }

        // Override memory pressure response to maintain user's limit
        tileServicesClass.declaredMethods
            .find { it.name == "setMemoryPressure" && it.parameterCount == 1 }
            ?.let {
                module.hook(it, SetMemoryPressureHooker::class.java)
                log("$TAG Hooked setMemoryPressure")
            }

        // Diagnostic only, logs bound vs unbound tiles
        tileServicesClass.declaredMethods
            .find { it.name == "recalculateBindAllowance" && it.parameterCount == 0 }
            ?.let {
                module.hook(it, RecalculateBindAllowanceHooker::class.java)
                log("$TAG Hooked recalculateBindAllowance")
            }

        log("$TAG Setup complete")
    }

    fun setMaxBound(
        tileServices: Any,
        value: Int,
    ) {
        runCatching {
            val field = maxBoundField ?: return
            val oldValue = field.getInt(tileServices)
            field.setInt(tileServices, value)
            log("$TAG mMaxBound $oldValue → $value")
        }.onFailure {
            log("$TAG Failed to set mMaxBound: ${it.message}")
        }
    }

    fun getMaxBound(tileServices: Any): Int =
        runCatching { maxBoundField?.getInt(tileServices) }.getOrNull() ?: -1
}

@XposedHooker
class TileServicesConstructorHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val tileServices = callback.thisObject ?: return
            val userMax = PrefsManager.getMaxBound()
            val newMax = maxOf(PrefsManager.DEFAULT_MAX_BOUND, userMax)
            log("$TAG_CTOR mMaxBound ${PrefsManager.DEFAULT_MAX_BOUND} → $newMax")
            TileServicesHook.setMaxBound(tileServices, newMax)
        }
    }
}

@XposedHooker
class SetMemoryPressureHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val tileServices = callback.thisObject ?: return
            val memoryPressure = callback.args[0] as? Boolean ?: return
            val newMax = maxOf(PrefsManager.DEFAULT_MAX_BOUND, PrefsManager.getMaxBound())
            log("$TAG_MEMORY pressure=$memoryPressure, forcing mMaxBound=$newMax")
            TileServicesHook.setMaxBound(tileServices, newMax)
        }
    }
}

@XposedHooker
class RecalculateBindAllowanceHooker : XposedInterface.Hooker {
    companion object {
        private var bindAllowedField: Field? = null
        private var stateManagerField: Field? = null
        private var intentField: Field? = null

        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val tileServices = callback.thisObject ?: return
            val currentMax = TileServicesHook.getMaxBound(tileServices)

            runCatching {
                val servicesField = tileServices.javaClass.getDeclaredField("mServices")
                servicesField.isAccessible = true
                val services = servicesField.get(tileServices) as? Map<*, *> ?: return

                val bound = mutableListOf<String>()
                val unbound = mutableListOf<String>()

                for ((_, manager) in services) {
                    val pkg = getPackageName(manager!!) ?: "unknown"
                    val isAllowed = isBindAllowed(manager)
                    if (isAllowed) bound.add(pkg) else unbound.add(pkg)
                }

                log("$TAG_BIND bound=${bound.size}/$currentMax: $bound")
                if (unbound.isNotEmpty()) {
                    log("$TAG_BIND unbound=${unbound.size}: $unbound")
                }
            }.onFailure {
                log("$TAG_BIND error: ${it.message}")
            }
        }

        private fun isBindAllowed(manager: Any): Boolean =
            runCatching {
                if (bindAllowedField == null) {
                    bindAllowedField = manager.javaClass.getDeclaredField("mBindAllowed")
                    bindAllowedField!!.isAccessible = true
                }
                bindAllowedField!!.getBoolean(manager)
            }.getOrDefault(false)

        private fun getPackageName(manager: Any): String? =
            runCatching {
                if (stateManagerField == null) {
                    stateManagerField = manager.javaClass.getDeclaredField("mStateManager")
                    stateManagerField!!.isAccessible = true
                }
                val stateManager = stateManagerField!!.get(manager) ?: return@runCatching null

                if (intentField == null) {
                    intentField = stateManager.javaClass.getDeclaredField("mIntent")
                    intentField!!.isAccessible = true
                }
                val intent = intentField!!.get(stateManager) as? android.content.Intent
                intent?.component?.packageName
            }.getOrNull()
    }
}
