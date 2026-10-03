package org.gnu.ymir.intellij.debug

import com.intellij.execution.ExecutionException
import com.intellij.util.execution.ParametersListUtil
import org.gnu.ymir.intellij.run.GyllirCommand
import org.gnu.ymir.intellij.run.GyllirPackages
import org.gnu.ymir.intellij.run.GyllirRunConfiguration
import org.gnu.ymir.intellij.run.GyllirTarget
import java.nio.file.Path

/**
 * What debugging a gyllir run configuration builds, `gyllir <build>` in the
 * package `directory`, and then runs under gdb: `program` with `arguments`.
 */
data class GyllirDebugTarget(val directory: Path, val build: List<String>, val program: Path, val arguments: List<String>) {

    companion object {

        fun of(configuration: GyllirRunConfiguration): GyllirDebugTarget =
            of(configuration.command, Path.of(configuration.workingDirectory), ParametersListUtil.parse(configuration.arguments))

        /**
         * The program `gyllir <command> <arguments>` runs in `dir`, built in debug: the target `gyllir run` runs,
         * named first in the arguments when the package has several, or the tests of the target `gyllir test`
         * runs, built by `gyllir test --dry`.
         */
        fun of(command: GyllirCommand, dir: Path, arguments: List<String>): GyllirDebugTarget {
            val targets = GyllirPackages.targets(dir)
            return when (command) {
                GyllirCommand.RUN -> {
                    val named = arguments.firstOrNull()?.takeIf { it in targets }
                    val forwarded = if (named != null || arguments.firstOrNull() == "--") arguments.drop(1) else arguments
                    val target = targets[named] ?: targets.values.singleOrNull()
                        ?: throw ExecutionException("Name the target to debug first in the arguments, ${names(targets.values)}")
                    if (target.library) {
                        throw ExecutionException("The target '${target.name}' is a library, debug its tests instead")
                    }
                    GyllirDebugTarget(dir, listOf("build", target.name), dir.resolve(target.output), forwarded)
                }
                GyllirCommand.TEST -> {
                    val named = arguments.firstOrNull { it in targets }
                    val testable = targets.values.filter { it.tests }
                    val target = targets[named] ?: testable.singleOrNull()
                        ?: throw ExecutionException("Name the target whose tests to debug in the arguments, ${names(testable)}")
                    GyllirDebugTarget(dir, listOf("test", "--dry") + arguments, dir.resolve("${target.output}.test"), emptyList())
                }
                GyllirCommand.BUILD -> throw ExecutionException("'gyllir build' runs no program to debug")
            }
        }

        private fun names(targets: Collection<GyllirTarget>): String =
            if (targets.isEmpty()) "the package has none" else "among ${targets.map { it.name }.sorted().joinToString(", ")}"
    }
}
