
package eu.hxreborn.qsboundlesstiles.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    suspend fun getActiveQsTileCount(): Int =
        withContext(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    val p = ProcessBuilder("su", "-c", "settings get secure sysui_qs_tiles")
                        .redirectErrorStream(true).start()
                    // Read stdout concurrently to avoid pipe buffer deadlock
                    val outputDeferred = async(Dispatchers.IO) {
                        p.inputStream.bufferedReader().use { it.readText().trim() }
                    }
                    val completed = p.waitFor(DEFAULT_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    if (!completed) {
                        p.destroyForcibly()
                        outputDeferred.cancel()
                        return@coroutineScope 0
                    }
                    if (p.exitValue() != 0) return@coroutineScope 0
                    val tileSpec = outputDeferred.await()
                    if (tileSpec == "null" || tileSpec.isBlank()) return@coroutineScope 0
                    tileSpec.split(",").count { it.startsWith("custom(") }
                }
            }.getOrDefault(0)
        }

    private suspend fun runAsRoot(
        cmd: String,
        timeout: Duration,
        stdoutOk: (String) -> Boolean = { true },
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
                    // Read stdout concurrently to avoid pipe buffer deadlock
                    val outputDeferred = async(Dispatchers.IO) {
                        p.inputStream.bufferedReader().use { it.readText() }
                    }
                    val completed = p.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    if (!completed) {
                        p.destroyForcibly()
                        outputDeferred.cancel()
                        return@coroutineScope false
                    }
                    p.exitValue() == 0 && stdoutOk(outputDeferred.await())
                }
            }.getOrDefault(false)
        }
}
