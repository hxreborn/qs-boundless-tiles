package eu.hxreborn.qsboundlesstiles.prefs

import android.content.Context
import android.os.IBinder
import android.os.RemoteException
import android.util.Log

object HookBinderHolder : IBinder.DeathRecipient {
    private const val TAG = "QSBoundlessTiles"

    @Volatile
    private var binder: IBinder? = null

    fun link(
        binder: IBinder,
        context: Context,
        timestamp: Long,
        boundLimit: Int,
        qsCount: Int,
    ) {
        runCatching { this.binder?.unlinkToDeath(this, 0) }

        this.binder = binder
        try {
            binder.linkToDeath(this, 0)
        } catch (e: RemoteException) {
            Log.e(TAG, "HookBinderHolder: linkToDeath failed", e)
            this.binder = null
            return
        }

        AppPrefsHelper.setHookTimestamp(context, timestamp)
        AppPrefsHelper.setHookBoundLimit(context, boundLimit)
        AppPrefsHelper.setActiveQsCount(context, qsCount)

        Log.d(TAG, "HookBinderHolder: linked, boundLimit=$boundLimit, qsCount=$qsCount")
    }

    override fun binderDied() {
        Log.d(TAG, "HookBinderHolder: binderDied - hook is dead")
        binder = null
    }

    fun isAlive(): Boolean = binder != null
}
