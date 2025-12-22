package eu.hxreborn.qsboundlesstiles.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object RootUtils {
    private val defaultTimeout = 2.seconds

    suspend fun isRootAvailable(): Boolean =
        execAsRoot("id", defaultTimeout).getOrNull()?.contains("uid=0") == true

    suspend fun restartSystemUI(): Boolean =
        execAsRoot("killall com.android.systemui", 10.seconds).isSuccess

    suspend fun getActiveQsTileCount(): Int =
        execAsRoot("settings get secure sysui_qs_tiles", defaultTimeout)
            .map { it.countCustomTiles() }
            .getOrDefault(0)

    private fun String.countCustomTiles(): Int =
        splitToSequence(',')
            .map { it.trim() }
            .count { it.startsWith("custom(") }

    private suspend fun execAsRoot(
        cmd: String,
        timeout: Duration,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    val p =
                        ProcessBuilder("su", "-c", cmd)
                            .redirectErrorStream(true)
                            .start()

                    // Read stdout concurrently to avoid pipe buffer deadlock
                    val outputDeferred =
                        async {
                            p.inputStream.bufferedReader().use { it.readText().trim() }
                        }

                    val completed =
                        runInterruptible {
                            p.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                        }

                    if (!completed) {
                        p.destroyForcibly()
                        outputDeferred.cancel()
                        error("Command timed out: $cmd")
                    }

                    val exitCode = p.exitValue()
                    val output = outputDeferred.await()

                    if (exitCode != 0) {
                        error("Command failed (exit $exitCode): $cmd")
                    }

                    if (output == "null" || output.isBlank()) "" else output
                }
            }
        }
}
