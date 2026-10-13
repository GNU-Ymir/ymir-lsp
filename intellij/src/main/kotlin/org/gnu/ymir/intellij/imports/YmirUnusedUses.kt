package org.gnu.ymir.intellij.imports

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * The `use` declarations of each file that the latest diagnostics the
 * language server published for it in the session report unused, the paths
 * of the E3038 warnings of ymirc.
 */
@Service(Service.Level.PROJECT)
class YmirUnusedUses {

    private val byFile = ConcurrentHashMap<String, Set<List<String>>>()

    /** Replaces the unused paths of the file `uri` by the ones of its diagnostics `diagnostics`. */
    fun update(uri: String, diagnostics: List<Diagnostic>) {
        val path = pathOf(uri) ?: return
        val unused = diagnostics.mapNotNull { unusedPath(it) }.toSet()
        if (unused.isEmpty()) byFile.remove(path) else byFile[path] = unused
    }

    /** The paths of the unused `use` declarations of `file`, their segments, as of its latest diagnostics. */
    fun of(file: VirtualFile): Set<List<String>> = byFile[file.path] ?: emptySet()

    companion object {
        /** The code of the warning of ymirc reporting a `use` resolving no symbol. */
        const val CODE = "E3038"

        private val MESSAGE = Regex("""\buse of (\S+)$""")

        fun getInstance(project: Project): YmirUnusedUses = project.getService(YmirUnusedUses::class.java)

        /** The segments of the path whose `use` the diagnostic `d` reports unused, null if it reports something else. */
        fun unusedPath(d: Diagnostic): List<String>? {
            val code = d.code?.let { if (it.isLeft) it.left else it.right?.toString() }
            if (code != CODE) return null
            val path = MESSAGE.find(messageOf(d).lineSequence().first().trim())?.groupValues?.get(1) ?: return null
            return path.split("::").takeIf { s -> s.all { it.isNotEmpty() } }
        }

        /** The message of `d`, a `String` in the lsp4j of the IDE compiled against, an `Either` in the one of LSP4IJ. */
        private fun messageOf(d: Diagnostic): String = when (val m = Diagnostic::class.java.getMethod("getMessage").invoke(d)) {
            is String -> m
            is Either<*, *> -> (if (m.isLeft) m.left else (m.right as? MarkupContent)?.value)?.toString() ?: ""
            else -> ""
        }

        private fun pathOf(uri: String): String? = try {
            Paths.get(URI(uri)).toString()
        } catch (_: Exception) {
            null
        }
    }
}
