package org.gnu.ymir.intellij.run

import com.intellij.openapi.util.TextRange

/**
 * A `__test` of a file, `range` being its keyword, and the `--filter` of
 * `gyllir test` running it alone, none when the module of the file is unknown.
 */
data class GyllirTest(val range: TextRange, val filter: String?)

object GyllirTests {

    /**
     * The tests of `text`, the content of a file of the module `module`, as
     * the test runner names them: `module::name`, `module::name[*]` for the
     * cases of a parameterized one, and an unnamed one by its rank among the
     * tests of the module.
     */
    fun of(text: CharSequence, module: String?): List<GyllirTest> =
        TEST.findAll(text).mapIndexed { index, match ->
            val keyword = match.groups[1]!!.range
            val name = match.groups[2]?.value ?: index.toString()
            val case = if (match.groups[3] != null) "$name[*]" else name
            GyllirTest(TextRange(keyword.first, keyword.last + 1), module?.let { "$it::$case" })
        }.toList()

    private val TEST = Regex("""^[ \t]*(?:pub[ \t]+)?(__test)\b[ \t]*([A-Za-z_][A-Za-z0-9_]*)?[ \t]*(\()?""", RegexOption.MULTILINE)
}
