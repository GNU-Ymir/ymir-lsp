package org.gnu.ymir.intellij.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VfsUtilCore
import org.gnu.ymir.intellij.run.GyllirPackages
import java.nio.file.Path

/**
 * Excludes from a project rooted at a gyllir package the `.deps` its
 * dependencies are resolved in, which are not of the project.
 */
class GyllirExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {

    override fun getExcludeUrlsForProject(): Array<String> {
        val dir = project.basePath?.let { Path.of(it) } ?: return emptyArray()
        return excluded(dir).map { VfsUtilCore.pathToUrl(it.toString()) }.toTypedArray()
    }

    companion object {
        const val DEPS = ".deps"

        /** The directories excluded from a project rooted at `dir`. */
        fun excluded(dir: Path): List<Path> =
            if (GyllirPackages.isPackage(dir)) listOf(dir.resolve(DEPS)) else emptyList()
    }
}
