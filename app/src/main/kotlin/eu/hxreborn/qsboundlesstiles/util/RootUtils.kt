package eu.hxreborn.qsboundlesstiles.util

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootUtils {
    private const val CMD_RESTART_SYSTEM_UI = "killall com.android.systemui"
    private const val CMD_GET_ACTIVE_QS_TILES = "settings get secure sysui_qs_tiles"

    suspend fun isRootAvailable(): Boolean =
        io {
            runCatching { Shell.getShell().isRoot }.getOrDefault(false)
        }

    suspend fun restartSystemUI(): Boolean = exec(CMD_RESTART_SYSTEM_UI).isSuccess

    suspend fun getActiveQsTileCount(): Int =
        exec(CMD_GET_ACTIVE_QS_TILES)
            .takeIf { it.isSuccess }
            ?.out
            ?.firstOrNull()
            .orEmpty()
            .countCustomTiles()

    private suspend fun exec(command: String): Shell.Result =
        io {
            Shell.cmd(command).exec()
        }

    private suspend fun <T> io(block: () -> T): T =
        withContext(Dispatchers.IO) {
            block()
        }

    private fun String.countCustomTiles(): Int =
        splitToSequence(',').map { it.trim() }.count { it.startsWith("custom(") }
}
