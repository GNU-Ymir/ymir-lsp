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
import java.nio.file.Files
import java.nio.file.Path

class GyllirRunConfigurationOptions : RunConfigurationOptions() {
    var command by string(GyllirCommand.RUN.id)
    var arguments by string("")
    var workingDirectory by string("")
    var filter by string("")
    var envFile by string("")
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

    /** The tests `test` runs, by the `--filter` of gyllir, all of them when not set. */
    var filter: String
        get() = options.filter.orEmpty()
        set(value) {
            options.filter = value
        }

    /** The file of `KEY=VALUE` lines `test` sets in the environment of the tests, none when not set. */
    var envFile: String
        get() = options.envFile.orEmpty()
        set(value) {
            options.envFile = value
        }

    /** The environment file, relative to the directory of the package. */
    fun envFilePath(): Path? = envFile.takeIf { it.isNotBlank() }?.let { Path.of(workingDirectory).resolve(it) }

    fun suggestedName(): String = "gyllir ${command.id}"

    override fun checkConfiguration() {
        if (!GyllirPackages.isPackage(Path.of(workingDirectory))) {
            throw RuntimeConfigurationError("No ${GyllirPackages.MANIFEST} in '$workingDirectory'")
        }
        val env = envFilePath()
        if (command == GyllirCommand.TEST && env != null && !Files.isRegularFile(env)) {
            throw RuntimeConfigurationError("No environment file '$env'")
        }
    }

    /** `gyllir <command> <arguments>`, in the directory of the package, preceded by the filter and the environment file of `test`. */
    fun commandLine(): GeneralCommandLine =
        GeneralCommandLine(YmirSettings.getInstance().gyllirPath, command.id)
            .withParameters(testOptions())
            .withParameters(ParametersListUtil.parse(arguments))
            .withWorkingDirectory(Path.of(workingDirectory))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

    private fun testOptions(): List<String> {
        if (command != GyllirCommand.TEST) return emptyList()
        return (if (envFile.isNotBlank()) listOf("--env-file", envFile) else emptyList()) +
            (if (filter.isNotBlank()) listOf("--filter", filter) else emptyList())
    }

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
