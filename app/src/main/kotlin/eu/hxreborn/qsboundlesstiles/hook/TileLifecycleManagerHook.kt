package eu.hxreborn.qsboundlesstiles.hook

import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import eu.hxreborn.qsboundlesstiles.QSBoundlessTilesModule.Companion.log
import eu.hxreborn.qsboundlesstiles.module
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

private const val TILE_LIFECYCLE_MANAGER_CLASS =
    "com.android.systemui.qs.external.TileLifecycleManager"

private const val MSG_ON_ADDED = 0
private const val MSG_ON_REMOVED = 1
private const val MSG_ON_CLICK = 2
private const val MSG_ON_UNLOCK_COMPLETE = 3

private var intentField: Field? = null
private var queuedMessagesField: Field? = null

private val bindStartTimes = ConcurrentHashMap<String, Long>()

object TileLifecycleManagerHook {
    fun hook(classLoader: ClassLoader) {
        log("TileLifecycleManagerHook: Starting hook...")

        val tlmClass = classLoader.loadClass(TILE_LIFECYCLE_MANAGER_CLASS)

        tlmClass.declaredMethods
            .find {
                it.name == "onClick" && it.parameterCount == 1 &&
                    it.parameterTypes[0] == IBinder::class.java
            }?.let {
                module.hook(it, OnClickHooker::class.java)
                log("TileLifecycleManagerHook: Hooked onClick")
            }

        tlmClass.declaredMethods
            .find {
                it.name == "setBindService" && it.parameterCount == 1 &&
                    it.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }?.let {
                module.hook(it, SetBindServiceHooker::class.java)
                log("TileLifecycleManagerHook: Hooked setBindService")
            }

        tlmClass.declaredMethods
            .find {
                it.name == "onServiceConnected" && it.parameterCount == 2 &&
                    it.parameterTypes[0] == ComponentName::class.java
            }?.let {
                module.hook(it, OnServiceConnectedHooker::class.java)
                log("TileLifecycleManagerHook: Hooked onServiceConnected")
            }

        tlmClass.declaredMethods
            .find { it.name == "onStartListening" && it.parameterCount == 0 }
            ?.let {
                module.hook(it, OnStartListeningHooker::class.java)
                log("TileLifecycleManagerHook: Hooked onStartListening")
            }

        tlmClass.declaredMethods
            .find { it.name == "onStopListening" && it.parameterCount == 0 }
            ?.let {
                module.hook(it, OnStopListeningHooker::class.java)
                log("TileLifecycleManagerHook: Hooked onStopListening")
            }

        tlmClass.declaredMethods
            .find { it.name == "handlePendingMessages" && it.parameterCount == 0 }
            ?.let {
                module.hook(it, HandlePendingMessagesHooker::class.java)
                log("TileLifecycleManagerHook: Hooked handlePendingMessages")
            }

        tlmClass.declaredMethods
            .find { it.name == "hasPendingClick" && it.parameterCount == 0 }
            ?.let {
                module.hook(it, HasPendingClickHooker::class.java)
                log("TileLifecycleManagerHook: Hooked hasPendingClick")
            }

        log("TileLifecycleManagerHook: Hook setup complete")
    }
}

private fun getComponent(tlm: Any): ComponentName? =
    runCatching {
        if (intentField == null) {
            intentField = tlm.javaClass.getDeclaredField("mIntent")
            intentField!!.isAccessible = true
        }
        (intentField!!.get(tlm) as? Intent)?.component
    }.getOrNull()

private fun getQueuedMessages(tlm: Any): Set<Int>? =
    runCatching {
        if (queuedMessagesField == null) {
            queuedMessagesField = tlm.javaClass.getDeclaredField("mQueuedMessages")
            queuedMessagesField!!.isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        queuedMessagesField!!.get(tlm) as? Set<Int>
    }.getOrNull()

private fun messageToString(msg: Int): String =
    when (msg) {
        MSG_ON_ADDED -> "ADDED"
        MSG_ON_REMOVED -> "REMOVED"
        MSG_ON_CLICK -> "CLICK"
        MSG_ON_UNLOCK_COMPLETE -> "UNLOCK"
        else -> "UNKNOWN($msg)"
    }

@XposedHooker
class OnClickHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun before(callback: BeforeHookCallback) {
            val tlm = callback.thisObject ?: return
            val component = getComponent(tlm)
            log("TILE_CLICK pkg=${component?.packageName} ts=${System.currentTimeMillis()}")
        }
    }
}

@XposedHooker
class SetBindServiceHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun before(callback: BeforeHookCallback) {
            val tlm = callback.thisObject ?: return
            val bind = callback.args[0] as? Boolean ?: return
            val pkg = getComponent(tlm)?.packageName ?: return
            val ts = System.currentTimeMillis()

            if (bind) {
                bindStartTimes[pkg] = ts
                log("BIND_START pkg=$pkg ts=$ts")
            } else {
                bindStartTimes.remove(pkg)
                log("BIND_STOP pkg=$pkg ts=$ts")
            }
        }
    }
}

@XposedHooker
class OnServiceConnectedHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val component = callback.args[0] as? ComponentName ?: return
            val pkg = component.packageName
            val ts = System.currentTimeMillis()
            val startTime = bindStartTimes[pkg]
            val duration = if (startTime != null) ts - startTime else -1
            log("BIND_COMPLETE pkg=$pkg ts=$ts duration_ms=$duration")
        }
    }
}

@XposedHooker
class OnStartListeningHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun before(callback: BeforeHookCallback) {
            val tlm = callback.thisObject ?: return
            log(
                "LISTENING_START pkg=${getComponent(
                    tlm,
                )?.packageName} ts=${System.currentTimeMillis()}",
            )
        }
    }
}

@XposedHooker
class OnStopListeningHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun before(callback: BeforeHookCallback) {
            val tlm = callback.thisObject ?: return
            log(
                "LISTENING_STOP pkg=${getComponent(
                    tlm,
                )?.packageName} ts=${System.currentTimeMillis()}",
            )
        }
    }
}

@XposedHooker
class HandlePendingMessagesHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun before(callback: BeforeHookCallback) {
            val tlm = callback.thisObject ?: return
            val queued = getQueuedMessages(tlm)?.takeIf { it.isNotEmpty() } ?: return
            val queuedStr = queued.joinToString(",") { messageToString(it) }
            log(
                "PENDING_MSGS pkg=${getComponent(
                    tlm,
                )?.packageName} queued=[$queuedStr] ts=${System.currentTimeMillis()}",
            )
        }
    }
}

@XposedHooker
class HasPendingClickHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val tlm = callback.thisObject ?: return
            val hasPending = callback.result as? Boolean ?: return
            log("PENDING_CLICK pkg=${getComponent(tlm)?.packageName} has_pending=$hasPending")
        }
    }
}
