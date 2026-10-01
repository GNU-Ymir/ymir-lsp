package org.gnu.ymir.intellij.run

import com.intellij.execution.RunManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Gives a project rooted at a gyllir package its run configurations, the
 * first time it is opened: running the program and its tests, or building a
 * library and running its tests.
 */
class GyllirProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val dir = project.basePath?.let { Path.of(it) } ?: return
        if (!GyllirPackages.isPackage(dir)) return

        val commands = if (GyllirPackages.isLibrary(dir)) {
            listOf(GyllirCommand.TEST, GyllirCommand.BUILD)
        } else {
            listOf(GyllirCommand.RUN, GyllirCommand.TEST)
        }

        withContext(Dispatchers.EDT) {
            val manager = RunManager.getInstance(project)
            if (manager.getConfigurationSettingsList(GyllirConfigurationType.getInstance()).isNotEmpty()) {
                return@withContext
            }

            val created = commands.map { GyllirRuns.create(project, dir, it) }
            created.forEach { manager.addConfiguration(it) }
            manager.selectedConfiguration = created.first()
        }
    }
}
