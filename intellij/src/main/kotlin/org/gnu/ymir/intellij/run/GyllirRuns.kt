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

    /** The configuration running `command` on the package `dir`, a temporary one when the project has none. */
    fun settingsFor(project: Project, dir: Path, command: GyllirCommand): RunnerAndConfigurationSettings {
        val manager = RunManager.getInstance(project)
        manager.getConfigurationSettingsList(GyllirConfigurationType.getInstance())
            .firstOrNull { matches(it, dir, command) }
            ?.let { return it }

        val settings = create(project, dir, command)
        manager.setTemporaryConfiguration(settings)
        return settings
    }

    /** A new configuration running `command` on the package `dir`, named after both. */
    fun create(project: Project, dir: Path, command: GyllirCommand): RunnerAndConfigurationSettings {
        val atRoot = project.basePath?.let { Path.of(it) == dir } ?: false
        val name = if (atRoot) command.label else "${command.label} ${dir.fileName}"
        val settings = RunManager.getInstance(project)
            .createConfiguration(name, GyllirConfigurationType.getInstance().factory)
        val configuration = settings.configuration as GyllirRunConfiguration
        configuration.command = command
        configuration.workingDirectory = dir.toString()
        return settings
    }

    /** Selects the configuration running `command` on `dir`, and runs it with `executor`, or debugs it. */
    fun run(project: Project, dir: Path, command: GyllirCommand, executor: Executor = DefaultRunExecutor.getRunExecutorInstance()) {
        val settings = settingsFor(project, dir, command)
        RunManager.getInstance(project).selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, executor)
    }

    fun matches(settings: RunnerAndConfigurationSettings, dir: Path, command: GyllirCommand): Boolean {
        val configuration = settings.configuration as? GyllirRunConfiguration ?: return false
        return configuration.command == command && Path.of(configuration.workingDirectory) == dir
    }
}
