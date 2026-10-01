package org.gnu.ymir.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * Starts `ymir-lsp` on stdio, in the directory of the project, with the
 * environment of a login shell so that a server installed in the PATH of the
 * user is found.
 */
class YmirLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        val commandLine = GeneralCommandLine(YmirSettings.getInstance().serverPath)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        project.basePath?.let { commandLine.withWorkDirectory(it) }
        return OSProcessStreamConnectionProvider(commandLine)
    }
}
