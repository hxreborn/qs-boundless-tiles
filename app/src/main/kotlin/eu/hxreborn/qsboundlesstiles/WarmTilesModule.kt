package eu.hxreborn.qsboundlesstiles

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

private const val TAG = "WarmTiles"
private const val TILE_SERVICES = "com.android.systemui.qs.external.TileServices"

class WarmTilesModule : XposedModule() {
    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        val field =
            runCatching {
                param.classLoader.loadClass(TILE_SERVICES).getDeclaredField("mMaxBound")
            }.getOrElse {
                log(Log.ERROR, TAG, "mMaxBound lookup failed", it)
                return
            }
        field.isAccessible = true
        val cap: Any =
            if (field.type == Byte::class.javaPrimitiveType) Byte.MAX_VALUE else Int.MAX_VALUE
        for (ctor in field.declaringClass.declaredConstructors) {
            hook(ctor).intercept { chain ->
                val result = chain.proceed()
                runCatching { field.set(chain.thisObject, cap) }
                    .onSuccess { log(Log.INFO, TAG, "mMaxBound=$cap") }
                    .onFailure { log(Log.ERROR, TAG, "set mMaxBound failed", it) }
                result
            }
        }
    }
}
