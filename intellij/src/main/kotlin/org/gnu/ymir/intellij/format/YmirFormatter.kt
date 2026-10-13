package org.gnu.ymir.intellij.format

import com.intellij.openapi.util.TextRange

/**
 * Formats a Ymir source without the language server, changing only its
 * whitespace: each line indented as [YmirIndenter] decides, a line continuing
 * the expression of the line before it moved along with it when it is indented
 * more than the line the expression starts on, the tokens of a
 * line spaced as [YmirSpacing] decides, the spaces aligning a `=`, a `=>`, a
 * `{` or a comment kept, the trailing spaces removed and the empty lines following each
 * other merged. The lines of a multi-line comment move with its first one, and
 * the ones of a string are left as they are.
 */
object YmirFormatter {

    private val PADDED = setOf("=", "=>", "{")

    /** `text` formatted, only the lines intersecting `ranges` if any are given. */
    fun format(text: String, ranges: List<TextRange>? = null, tabSize: Int = YmirIndenter.INDENT): String {
        val tokens = YmirLexer.tokens(text)
        val code = tokens.filter { !it.isComment }
        val codeIndex = HashMap<YmirToken, Int>().apply { code.forEachIndexed { k, t -> put(t, k) } }
        val nextCode = arrayOfNulls<YmirToken>(tokens.size + 1)
        for (k in tokens.indices.reversed()) nextCode[k] = if (tokens[k].isComment) nextCode[k + 1] else tokens[k]
        val spacing = YmirSpacing(code)
        val indenter = YmirIndenter()
        val out = StringBuilder()
        var outLine = 0
        var headDelta = 0
        var head = 0
        var prev: YmirToken? = null

        fun isFormatted(t: YmirToken): Boolean {
            if (ranges == null) return true
            val start = YmirIndenter.lineStart(text, t.start)
            val end = text.indexOf('\n', t.start).let { if (it < 0) text.length else it }
            return ranges.any { it.startOffset <= end && start <= it.endOffset }
        }

        fun oldIndent(t: YmirToken): Int = YmirIndenter.column(text, YmirIndenter.lineStart(text, t.start), t.start, tabSize)

        fun continuing(t: YmirToken): Int = (oldIndent(t) + headDelta).let { if (it > head) it else indenter.indentOf(t) }

        for ((k, t) in tokens.withIndex()) {
            val gap = text.substring(prev?.end ?: 0, t.start)
            val newLine = k == 0 || '\n' in gap
            val formatted = isFormatted(t)
            if (!formatted) {
                out.append(gap)
            } else if (newLine && k > 0) {
                out.append("\n".repeat(minOf(gap.count { it == '\n' }, 2)))
                val indent = when {
                    t.isComment -> {
                        val next = nextCode[k]
                        if (next != null && indenter.continues(next)) continuing(next) else indenter.indentOf(null)
                    }
                    indenter.continues(t) -> continuing(t)
                    indenter.isInList() -> indenter.indentOf(t)
                    else -> indenter.indentOf(t).also {
                        headDelta = it - oldIndent(t)
                        head = it
                    }
                }
                out.append(" ".repeat(indent))
            } else if (!newLine && (prev!!.isComment || t.isComment)) {
                out.append(gap)
            } else if (!newLine && spacing.isSpaced(codeIndex[prev]!!, codeIndex[t]!!)) {
                out.append(if (gap.isEmpty() || t.text !in PADDED) " " else gap)
            }

            if (newLine) outLine = out.lastIndexOf("\n") + 1
            val col = YmirIndenter.column(out, outLine, out.length, tabSize)
            if (newLine) indenter.startLine(col, t)

            if (formatted && t.kind == YmirToken.Kind.BLOCK_COMMENT && '\n' in t.text) {
                out.append(moved(t.text, col - oldIndent(t)))
            } else if (formatted && t.kind == YmirToken.Kind.LINE_COMMENT) {
                out.append(t.text.trimEnd())
            } else {
                out.append(t.text)
            }

            if ('\n' in t.text) outLine = out.lastIndexOf("\n") + 1
            indenter.read(t, col)
            prev = t
        }

        val tail = text.substring(prev?.end ?: 0)
        if (prev == null || !isFormatted(prev)) out.append(tail) else if ('\n' in tail) out.append("\n")
        return out.toString()
    }

    /** The block comment `comment`, each continuation line moved by `delta` columns, its trailing spaces removed. */
    private fun moved(comment: String, delta: Int): String = comment.split('\n').mapIndexed { n, l ->
        val line = l.trimEnd()
        when {
            n == 0 || line.isEmpty() -> line
            delta >= 0 -> " ".repeat(delta) + line
            else -> line.drop(minOf(-delta, line.length - line.trimStart().length))
        }
    }.joinToString("\n")
}
