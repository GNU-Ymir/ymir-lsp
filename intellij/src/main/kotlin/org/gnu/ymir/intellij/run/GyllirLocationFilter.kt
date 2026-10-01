package org.gnu.ymir.intellij.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path

/**
 * Links the locations of the diagnostics of gyc, `src/main.yr:(4,9)`, to
 * their file, resolved against the directory of the package.
 */
class GyllirLocationFilter(private val project: Project, private val dir: Path) : Filter {

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val start = entireLength - line.length
        val items = LOCATION.findAll(line).mapNotNull { match ->
            val (path, row, column) = match.destructured
            val file = LocalFileSystem.getInstance().findFileByNioFile(dir.resolve(path)) ?: return@mapNotNull null
            val link = OpenFileHyperlinkInfo(project, file, row.toInt() - 1, column.toInt() - 1)
            Filter.ResultItem(start + match.range.first, start + match.range.last + 1, link)
        }.toList()
        return if (items.isEmpty()) null else Filter.Result(items)
    }

    private companion object {
        val LOCATION = Regex("""([^\s()]+\.yr):\((\d+),(\d+)\)""")
    }
}
