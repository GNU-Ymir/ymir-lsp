package org.gnu.ymir.intellij.run

import com.intellij.execution.ExecutionException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The environment files of `gyllir test --env-file`, read as gyllir reads
 * them: a `KEY=VALUE` a line, the blank lines and the ones starting with `#`
 * skipped, a leading `export ` ignored, and a value quoted by `"` or `'` unquoted.
 */
object GyllirEnvFile {

    fun read(path: Path): Map<String, String> {
        val content = try {
            Files.readString(path)
        } catch (_: IOException) {
            throw ExecutionException("Failed to read the environment file '$path'")
        }
        return parse(content, path.toString())
    }

    fun parse(content: String, file: String): Map<String, String> {
        val variables = linkedMapOf<String, String>()
        content.split('\n').forEachIndexed { index, raw ->
            val line = raw.trim(' ', '\t', '\r')
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed

            val assign = if (line.length > 7 && line.startsWith("export ")) line.substring(7).trim(' ', '\t', '\r') else line
            val equal = assign.indexOf('=')
            if (equal < 0) throw ExecutionException("In '$file', line ${index + 1}, expected 'KEY=VALUE'")

            val name = assign.substring(0, equal).trim(' ', '\t', '\r')
            if (!NAME.matches(name)) throw ExecutionException("In '$file', line ${index + 1}, '$name' is not a variable name")
            variables[name] = unquote(assign.substring(equal + 1).trim(' ', '\t', '\r'))
        }
        return variables
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && (value[0] == '"' || value[0] == '\'') && value.last() == value[0]) {
            value.substring(1, value.length - 1)
        } else {
            value
        }

    private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
