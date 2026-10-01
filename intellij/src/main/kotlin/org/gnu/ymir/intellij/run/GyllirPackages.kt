package org.gnu.ymir.intellij.run

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * The gyllir packages files belong to, a package being the directory of a
 * `gyllir.toml`.
 */
object GyllirPackages {

    const val MANIFEST = "gyllir.toml"

    /** The directory of the package `file` belongs to, the innermost one. */
    fun packageOf(file: VirtualFile): VirtualFile? {
        var dir: VirtualFile? = if (file.isDirectory) file else file.parent
        while (dir != null) {
            if (dir.findChild(MANIFEST) != null) return dir
            dir = dir.parent
        }
        return null
    }

    fun isPackage(dir: Path): Boolean = Files.isRegularFile(dir.resolve(MANIFEST))

    /** Whether the package of `dir` builds a library, which `gyllir run` refuses. */
    fun isLibrary(dir: Path): Boolean = runCatching {
        Files.readAllLines(dir.resolve(MANIFEST)).any { TYPE_LIBRARY.matches(it) }
    }.getOrDefault(false)

    /** The command a file asks for: the tests for what is under `test/`, running the package otherwise. */
    fun commandFor(file: VirtualFile, pkg: VirtualFile): GyllirCommand {
        val test = pkg.findChild("test")
        if (test != null && (file == test || VfsUtilCore.isAncestor(test, file, false))) {
            return GyllirCommand.TEST
        }
        return if (isLibrary(pkg.toNioPath())) GyllirCommand.TEST else GyllirCommand.RUN
    }

    private val TYPE_LIBRARY = Regex("""^\s*type\s*=\s*"library"\s*(#.*)?$""")
}
