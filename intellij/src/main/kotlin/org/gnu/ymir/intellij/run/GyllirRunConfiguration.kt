package org.gnu.ymir.intellij.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationBase
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition
import org.gnu.ymir.intellij.YmirSettings
import org.gnu.ymir.intellij.debug.YmirDebugAdapterFactory
import java.nio.file.Path

class GyllirRunConfigurationOptions : RunConfigurationOptions() {
    var command by string(GyllirCommand.RUN.id)
    var arguments by string("")
    var workingDirectory by string("")
}

/**
 * `gyllir <command> <arguments>` in the directory of a package, or, debugged,
 * the program it runs under gdb, the debug adapter `YmirDebugAdapterFactory`.
 */
class GyllirRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    DAPRunConfigurationBase<GyllirRunConfigurationOptions>(project, factory, name) {

    public override fun getOptions(): GyllirRunConfigurationOptions =
        super.getOptions() as GyllirRunConfigurationOptions

    var command: GyllirCommand
        get() = GyllirCommand.of(options.command)
        set(value) {
            options.command = value.id
        }

    var arguments: String
        get() = options.arguments.orEmpty()
        set(value) {
            options.arguments = value
        }

    /** The directory of the package, the one of the project when not set. */
    var workingDirectory: String
        get() = options.workingDirectory?.takeIf { it.isNotBlank() } ?: project.basePath.orEmpty()
        set(value) {
            options.workingDirectory = value
        }

    fun suggestedName(): String = "gyllir ${command.id}"

    override fun checkConfiguration() {
        if (!GyllirPackages.isPackage(Path.of(workingDirectory))) {
            throw RuntimeConfigurationError("No ${GyllirPackages.MANIFEST} in '$workingDirectory'")
        }
    }

    /** `gyllir <command> <arguments>`, in the directory of the package. */
    fun commandLine(): GeneralCommandLine =
        GeneralCommandLine(YmirSettings.getInstance().gyllirPath, command.id)
            .withParameters(ParametersListUtil.parse(arguments))
            .withWorkingDirectory(Path.of(workingDirectory))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = GyllirSettingsEditor(project)

    override fun getDebugAdapterServer(): DebugAdapterServerDefinition? =
        DebugAdapterManager.getInstance().getDebugAdapterServerById(YmirDebugAdapterFactory.ID)

    /** Whether the debug adapter runs the configuration: debugging what `run` or `test` runs. */
    override fun canRun(executorId: String): Boolean =
        executorId == DefaultDebugExecutor.EXECUTOR_ID && command != GyllirCommand.BUILD

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
            return (super.getState(executor, environment) as DAPCommandLineState).apply {
                addConsoleFilters(GyllirLocationFilter(project, Path.of(workingDirectory)))
            }
        }

        return object : CommandLineState(environment) {
            init {
                addConsoleFilters(GyllirLocationFilter(project, Path.of(workingDirectory)))
            }

            override fun startProcess(): ProcessHandler {
                val handler = KillableColoredProcessHandler(commandLine())
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
    }
}
