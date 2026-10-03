package org.gnu.ymir.intellij.debug

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory

/**
 * gdb as the debug adapter of the `.yr` files, taking their breakpoints, and
 * debugging the programs of the gyllir run configurations.
 */
class YmirDebugAdapterFactory : DebugAdapterDescriptorFactory() {

    override fun createDebugAdapterDescriptor(options: RunConfigurationOptions, environment: ExecutionEnvironment): DebugAdapterDescriptor =
        YmirDebugAdapterDescriptor(options, environment, serverDefinition)

    override fun isDebuggableFile(file: VirtualFile, project: Project): Boolean = isDebuggable(file)

    /** A `.yr` file is debugged by the gyllir run configuration of its package, not by one of LSP4IJ. */
    override fun prepareConfiguration(configuration: RunConfiguration, file: VirtualFile, project: Project): Boolean = false

    companion object {
        /** The id of the debug adapter in `plugin.xml`. */
        const val ID = "ymir-gdb"

        fun isDebuggable(file: VirtualFile): Boolean = file.extension == "yr"
    }
}
