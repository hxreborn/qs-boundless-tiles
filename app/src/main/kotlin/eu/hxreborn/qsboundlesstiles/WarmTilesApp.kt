package eu.hxreborn.qsboundlesstiles

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

class WarmTilesApp : Application() {
    @Volatile
    private var mService: XposedService? = null

    private val listeners = CopyOnWriteArrayList<XposedServiceHelper.OnServiceListener>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        XposedServiceHelper.registerListener(
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(svc: XposedService) {
                    mService = svc
                    listeners.forEach { it.onServiceBind(svc) }
                }

                override fun onServiceDied(svc: XposedService) {
                    mService = null
                    listeners.forEach { it.onServiceDied(svc) }
                }
            },
        )
    }

    fun xposedService(): XposedService? = mService

    fun addServiceListener(listener: XposedServiceHelper.OnServiceListener) {
        listeners.add(listener)
        mService?.let { listener.onServiceBind(it) }
    }

    fun removeServiceListener(listener: XposedServiceHelper.OnServiceListener) {
        listeners.remove(listener)
    }

    companion object {
        private lateinit var instance: WarmTilesApp

        fun addServiceListener(listener: XposedServiceHelper.OnServiceListener) =
            instance.addServiceListener(listener)

        fun removeServiceListener(listener: XposedServiceHelper.OnServiceListener) =
            instance.removeServiceListener(listener)

        fun xposedService(): XposedService? = instance.xposedService()
    }
}
