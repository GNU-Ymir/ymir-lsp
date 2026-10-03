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

    /**
     * The targets of the package `dir` by name: its `[targets.<name>]` tables,
     * or the target named after the package when it declares none.
     */
    fun targets(dir: Path): Map<String, GyllirTarget> {
        val lines = runCatching { Files.readAllLines(dir.resolve(MANIFEST)) }.getOrDefault(emptyList())
        val root = mutableMapOf<String, String>()
        val tables = linkedMapOf<String, MutableMap<String, String>>()
        var table: MutableMap<String, String>? = root
        for (line in lines) {
            val header = HEADER.matchEntire(line)
            if (header != null) {
                val target = TARGET.matchEntire(header.groupValues[1].trim())
                table = target?.let { tables.getOrPut(it.groupValues[1].ifEmpty { it.groupValues[2] }) { mutableMapOf() } }
                continue
            }
            val entry = ENTRY.matchEntire(line) ?: continue
            table?.put(entry.groupValues[1], entry.groupValues[2].removeSurrounding("\""))
        }

        if (tables.isEmpty()) {
            val name = root["name"] ?: dir.fileName.toString()
            return mapOf(name to GyllirTarget(name, root, name))
        }
        return tables.mapValues { (name, entries) -> GyllirTarget(name, entries, entries["output"] ?: name) }
    }

    private val TYPE_LIBRARY = Regex("""^\s*type\s*=\s*"library"\s*(#.*)?$""")
    private val HEADER = Regex("""^\s*\[([^\[\]]+)]\s*(#.*)?$""")
    private val TARGET = Regex("""targets\.(?:"([^"]+)"|([A-Za-z0-9_-]+))""")
    private val ENTRY = Regex("""^\s*([A-Za-z0-9_-]+)\s*=\s*("[^"]*"|true|false)\s*(#.*)?$""")
}

/**
 * A target of a gyllir package, producing `output` in the directory of the
 * package, and `output.test` for its tests.
 */
data class GyllirTarget(val name: String, val library: Boolean, val tests: Boolean, val output: String) {

    constructor(name: String, entries: Map<String, String>, output: String) :
        this(name, entries["type"] == "library", entries["tests"] != "false", output)
}
