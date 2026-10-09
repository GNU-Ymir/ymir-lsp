package org.gnu.ymir.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path

/**
 * The memory the language server may use, in MiB, `0` for no limit. A systemd user scope kills the
 * server past it, without letting it swap, and the heap of its GC is capped by it where there is none.
 */
data class YmirMemoryBudget(val mebibytes: Int, val systemd: Boolean = systemdUserSession()) {

    val limited: Boolean get() = mebibytes > 0

    /** The command running `serverPath` under the budget. */
    fun commandLine(serverPath: String): GeneralCommandLine = when {
        !limited -> GeneralCommandLine(serverPath)
        systemd -> GeneralCommandLine(
            "systemd-run", "--user", "--scope", "--quiet",
            "-p", "MemoryMax=${mebibytes}M", "-p", "MemorySwapMax=0",
            serverPath,
        )
        else -> GeneralCommandLine(serverPath)
            .withEnvironment(GC_MAXIMUM_HEAP_SIZE, (mebibytes.toLong() shl 20).toString())
    }

    /** Whether a server ending on `exitCode`, its GC having reported running out of memory or not, was killed on the budget. */
    fun exceeded(exitCode: Int, gcOutOfMemory: Boolean): Boolean = when {
        !limited -> false
        systemd -> exitCode == KILLED
        else -> gcOutOfMemory
    }

    companion object {
        const val GC_MAXIMUM_HEAP_SIZE = "GC_MAXIMUM_HEAP_SIZE"

        /** The warning of the Boehm GC failing an allocation past its maximum heap size. */
        const val GC_OUT_OF_MEMORY = "Out of Memory! Heap size"

        /** The exit code of a process killed by SIGKILL, as the kernel kills a scope past its `MemoryMax`. */
        const val KILLED = 128 + 9

        fun of(settings: YmirSettings): YmirMemoryBudget = YmirMemoryBudget(settings.memoryBudget)

        /** Whether `systemd-run --user` can start a scope, a systemd user manager running. */
        fun systemdUserSession(): Boolean {
            if (!SystemInfo.isLinux || PathEnvironmentVariableUtil.findInPath("systemd-run") == null) return false
            val runtime = System.getenv("XDG_RUNTIME_DIR") ?: return false
            return Files.exists(Path.of(runtime, "systemd", "private"))
        }
    }
}
