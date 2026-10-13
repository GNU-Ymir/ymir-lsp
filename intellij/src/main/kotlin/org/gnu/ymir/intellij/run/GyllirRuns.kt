package org.gnu.ymir.intellij.run

import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * The gyllir run configurations of a project, found by package and command.
 */
object GyllirRuns {

    /**
     * The configuration running `command` on the package `dir`, the tests of `filter` for `test`, a
     * temporary one when the project has none: one of a filter taking the arguments and the
     * environment file of the configuration running all the tests of the package.
     */
    fun settingsFor(project: Project, dir: Path, command: GyllirCommand, filter: String = ""): RunnerAndConfigurationSettings {
        val manager = RunManager.getInstance(project)
        val all = manager.getConfigurationSettingsList(GyllirConfigurationType.getInstance())
        all.firstOrNull { matches(it, dir, command, filter) }?.let { return it }

        val settings = create(project, dir, command, filter)
        val base = all.firstOrNull { matches(it, dir, command) }?.configuration as? GyllirRunConfiguration
        if (filter.isNotEmpty() && base != null) {
            val configuration = settings.configuration as GyllirRunConfiguration
            configuration.arguments = base.arguments
            configuration.envFile = base.envFile
        }
        manager.setTemporaryConfiguration(settings)
        return settings
    }

    /** A new configuration running `command` on the package `dir`, named after both, or after the tests of `filter`. */
    fun create(project: Project, dir: Path, command: GyllirCommand, filter: String = ""): RunnerAndConfigurationSettings {
        val atRoot = project.basePath?.let { Path.of(it) == dir } ?: false
        val name = when {
            filter.isNotEmpty() -> if (atRoot) filter else "$filter (${dir.fileName})"
            atRoot -> command.label
            else -> "${command.label} ${dir.fileName}"
        }
        val settings = RunManager.getInstance(project)
            .createConfiguration(name, GyllirConfigurationType.getInstance().factory)
        val configuration = settings.configuration as GyllirRunConfiguration
        configuration.command = command
        configuration.workingDirectory = dir.toString()
        configuration.filter = filter
        return settings
    }

    /** Selects the configuration running `command` on `dir`, and runs it with `executor`, or debugs it. */
    fun run(
        project: Project,
        dir: Path,
        command: GyllirCommand,
        executor: Executor = DefaultRunExecutor.getRunExecutorInstance(),
        filter: String = "",
    ) {
        val settings = settingsFor(project, dir, command, filter)
        RunManager.getInstance(project).selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, executor)
    }

    fun matches(settings: RunnerAndConfigurationSettings, dir: Path, command: GyllirCommand, filter: String = ""): Boolean {
        val configuration = settings.configuration as? GyllirRunConfiguration ?: return false
        return configuration.command == command && Path.of(configuration.workingDirectory) == dir && configuration.filter == filter
    }
}
