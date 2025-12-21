
package eu.hxreborn.qsboundlesstiles.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object RootUtils {
    private val DEFAULT_TIMEOUT = 2.seconds

    suspend fun isRootAvailable(): Boolean =
        runAsRoot("id", timeout = DEFAULT_TIMEOUT) { it.contains("uid=0") }

    suspend fun restartSystemUI(): Boolean =
        runAsRoot("killall com.android.systemui", timeout = 10.seconds)

    private suspend fun runAsRoot(
        cmd: String,
        timeout: Duration,
        stdoutOk: (String) -> Boolean = { true },
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = ProcessBuilder("su", "-c", cmd).start()
                if (!p.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly()
                    return@runCatching false
                }
                p.exitValue() == 0 && stdoutOk(p.inputStream.bufferedReader().use { it.readText() })
            }.getOrDefault(false)
        }
}
