package org.gnu.ymir.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceSymbolFeature
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * Starts `ymir-lsp` on stdio, in the directory of the project, with the
 * environment of a login shell so that a server installed in the PATH of the
 * user is found, under the memory budget of the settings.
 */
class YmirLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        val settings = YmirSettings.getInstance()
        val budget = YmirMemoryBudget.of(settings)
        val commandLine = budget.commandLine(settings.serverPath)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        project.basePath?.let { commandLine.withWorkDirectory(it) }
        return YmirServerConnectionProvider(project, budget, commandLine)
    }

    /** The workspace symbols are listed by `YmirSymbolContributor`, with their module, rather than by LSP4IJ. */
    override fun createClientFeatures(): LSPClientFeatures =
        LSPClientFeatures().setWorkspaceSymbolFeature(object : LSPWorkspaceSymbolFeature() {
            override fun isEnabled(): Boolean = false
        })

    companion object {
        /** The id of the server in `plugin.xml`. */
        const val SERVER_ID = "ymir"
    }
}
