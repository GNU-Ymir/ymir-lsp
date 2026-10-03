package org.gnu.ymir.intellij.debug

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import org.gnu.ymir.intellij.YmirSettings
import org.gnu.ymir.intellij.YmirTextMateBundleProvider
import org.gnu.ymir.intellij.run.GyllirRunConfiguration
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Debugs the program of a gyllir run configuration with `gdb -i=dap`, gdb
 * speaking the Debug Adapter Protocol on its standard streams, once gyllir
 * built the program: the output of gyllir goes to the console rather than to
 * the protocol. gdb loads the program when it starts, for the breakpoints the
 * client sends before the launch to be set in it, and the launch request gives
 * it the arguments and the directory of the program.
 */
class YmirDebugAdapterDescriptor(
    options: RunConfigurationOptions,
    environment: ExecutionEnvironment,
    serverDefinition: DebugAdapterServerDefinition?,
) : DebugAdapterDescriptor(options, environment, serverDefinition) {

    private val target: GyllirDebugTarget by lazy {
        val configuration = environment.runProfile as? GyllirRunConfiguration
            ?: throw ExecutionException("Ymir programs are debugged by the Gyllir run configurations")
        GyllirDebugTarget.of(configuration)
    }

    override fun startServer(): ProcessHandler = startServer(commandLine(target, YmirSettings.getInstance(), script()))

    override fun getDapParameters(): Map<String, Any> = mapOf(
        "type" to YmirDebugAdapterFactory.ID,
        "args" to target.arguments,
        "cwd" to target.directory.toString(),
    )

    override fun getFileType(): FileType = FileTypeManager.getInstance().getFileTypeByExtension("yr")

    /** The `.yr` files, as for the factory: a session checks its breakpoints against the descriptor. */
    override fun isDebuggableFile(file: VirtualFile, project: Project): Boolean = YmirDebugAdapterFactory.isDebuggable(file)

    companion object {

        /** `gyllir <build>`, its output on the error stream, followed on success by gdb on the program, loading `script`. */
        fun commandLine(target: GyllirDebugTarget, settings: YmirSettings, script: Path?): GeneralCommandLine {
            val build = listOf(settings.gyllirPath) + target.build
            val gdb = listOf(settings.gdbPath, "-q", "-i=dap") +
                (script?.let { listOf("-x", it.toString()) } ?: emptyList()) +
                target.program.toString()
            return GeneralCommandLine("/bin/sh", "-c", "${shell(build)} </dev/null 1>&2 && exec ${shell(gdb)}")
                .withWorkingDirectory(target.directory)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        }

        /** The gdb script of the plugin, `gdb/ymir.py`, printing the frames and the values of Ymir. */
        fun script(): Path? {
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(YmirTextMateBundleProvider.PLUGIN_ID)) ?: return null
            return plugin.pluginPath.resolve("gdb").resolve("ymir.py").takeIf { it.isRegularFile() }
        }

        private fun shell(words: List<String>): String = words.joinToString(" ") { "'" + it.replace("'", "'\\''") + "'" }
    }
}
