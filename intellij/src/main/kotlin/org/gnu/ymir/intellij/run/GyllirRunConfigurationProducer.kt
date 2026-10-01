package org.gnu.ymir.intellij.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/**
 * Run from the context menu of a file of a gyllir package: its tests for a
 * file under `test/` or the package of a library, the program otherwise.
 */
class GyllirRunConfigurationProducer : LazyRunConfigurationProducer<GyllirRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory = GyllirConfigurationType.getInstance().factory

    override fun setupConfigurationFromContext(
        configuration: GyllirRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val (pkg, command) = target(context) ?: return false
        configuration.command = command
        configuration.workingDirectory = pkg.path
        configuration.name = configuration.suggestedName()
        return true
    }

    override fun isConfigurationFromContext(configuration: GyllirRunConfiguration, context: ConfigurationContext): Boolean {
        val (pkg, command) = target(context) ?: return false
        return configuration.command == command && configuration.workingDirectory == pkg.path
    }

    private fun target(context: ConfigurationContext): Pair<VirtualFile, GyllirCommand>? {
        val file = context.location?.virtualFile ?: return null
        if (!file.isDirectory && file.extension != "yr" && file.name != GyllirPackages.MANIFEST) return null
        val pkg = GyllirPackages.packageOf(file) ?: return null
        return pkg to GyllirPackages.commandFor(file, pkg)
    }
}
