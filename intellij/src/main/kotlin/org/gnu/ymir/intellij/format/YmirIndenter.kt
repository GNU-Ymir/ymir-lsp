package org.gnu.ymir.intellij.format

/**
 * The indentation of the lines of a Ymir source as its canonical form lays them
 * out, from the tokens of the lines before them: the content of a block, and of
 * a list whose opening bracket ends its line, is indented once more than the
 * line opening it; the lines of a list are aligned on its first element, and the
 * ones continuing the value of a named element, `name-> value`, on this value; the
 * body of an `if`, a loop or an `else` without braces, the value following a
 * `=` or a `=>` ending a line, and a line starting with an operator are
 * indented once; a line starting with a closing bracket is the one opening it,
 * or aligned on the list it closes.
 */
class YmirIndenter(private val step: Int = INDENT) {

    // `value` is the column of the value of the named element of a list being read
    private class Open(val bracket: String, val line: Line, var align: Int, val keyword: String?, var value: Int = NONE)

    // The indentation of a line the lines it opens are relative to, whether it continues an expression, and whether
    // it is the header of an `if`, a loop or an `else`
    private class Line(val indent: Int, val continues: Boolean, var header: Boolean = false)

    private val stack = ArrayList<Open>()

    private var line = Line(0, false)

    private var lineDepth = 0

    private var previous: YmirToken? = null

    private var hanging = NONE

    /** The indentation of the next line, starting with the token `first`, or empty if it is null. */
    fun indentOf(first: YmirToken?): Int {
        val top = stack.lastOrNull()
        if (top != null && isInList() && top.value >= 0 && (first == null || !first.isClosing())) return top.value
        if (top != null && isInList() && top.align >= 0) return top.align
        if (first != null && first.isClosing() && top != null) return top.line.indent
        if (isInList()) return top!!.line.indent + step
        if (first == null || !first.isPunctuation("{")) {
            if (hanging != NONE) return hanging
            if (line.header && stack.size <= lineDepth && previous?.text !in CLOSED) return line.indent + step
        }

        if (first != null && continues(first)) return if (line.continues) line.indent else line.indent + step
        return if (top == null) 0 else top.line.indent + step
    }

    /** Starts a line, whose first token is `first`, at the column `indent`. */
    fun startLine(indent: Int, first: YmirToken) {
        stack.lastOrNull()?.let { if (it.align == PENDING) it.align = NONE }
        lineDepth = stack.size
        if (!first.isComment) line = Line(indent, continues(first))
    }

    /** Reads the token `t`, of the current line, written at the column `col`. */
    fun read(t: YmirToken, col: Int) {
        if (t.isComment) return
        val top = stack.lastOrNull()
        if (top != null && top.align == PENDING) top.align = col
        if (top != null && top.value == PENDING) top.value = col

        hanging = NONE
        when {
            t.isOpening() -> {
                val keyword = previous?.takeIf { it.kind == YmirToken.Kind.KEYWORD }?.text
                stack.add(Open(t.text, line, if (t.text == "{") NONE else PENDING, keyword))
            }
            t.isClosing() -> {
                val open = stack.removeLastOrNull()
                if (open != null && stack.size < lineDepth) {
                    line = open.line
                    lineDepth = stack.size
                }

                if (open != null && t.text == ")" && (open.keyword == "if" || open.keyword == "while")) {
                    hanging = open.line.indent + step
                }
            }
            t.kind == YmirToken.Kind.KEYWORD && t.text in HEADERS && stack.size <= lineDepth -> {
                line.header = true
                if (t.text == "else") hanging = line.indent + step
            }
            t.isPunctuation("=") || t.isPunctuation("=>") -> hanging = line.indent + step
            t.isPunctuation("->") && isInList() -> top!!.value = PENDING
            t.isPunctuation(",") && isInList() -> top!!.value = NONE
        }

        previous = t
    }

    /** Whether a line starting with the token `first` continues the expression of the line before it, out of a list. */
    fun continues(first: YmirToken): Boolean = isContinuation(first) && !isInList()

    /** Whether the next line is in a list rather than in a block. */
    fun isInList(): Boolean = stack.lastOrNull().let { it != null && it.bracket != "{" }

    companion object {
        const val INDENT = 4

        /**
         * The indentation of the line starting at the offset `lineStart` of
         * `text`, null if it starts in a string or in a comment.
         */
        fun indentAt(text: CharSequence, lineStart: Int, tabSize: Int = INDENT): Int? {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val indenter = YmirIndenter()
            var prevEnd = -1
            for (t in YmirLexer.tokens(text.subSequence(0, lineEnd))) {
                if (t.start >= lineStart) return indenter.indentOf(t.takeIf { '\n' !in text.subSequence(lineStart, t.start) })
                if (t.end > lineStart) return null

                val col = column(text, lineStart(text, t.start), t.start, tabSize)
                if (prevEnd < 0 || '\n' in text.subSequence(prevEnd, t.start)) indenter.startLine(col, t)
                indenter.read(t, col)
                prevEnd = t.end
            }

            return indenter.indentOf(null)
        }

        /** The offset starting the line of the offset `at`. */
        fun lineStart(text: CharSequence, at: Int): Int {
            var i = at
            while (i > 0 && text[i - 1] != '\n') i--
            return i
        }

        /** The column of the offset `at` of the line starting at `from`, a tab moving it to the next multiple of `tabSize`. */
        fun column(text: CharSequence, from: Int, at: Int, tabSize: Int): Int {
            var col = 0
            for (i in from until at) col = if (text[i] == '\t') (col / tabSize + 1) * tabSize else col + 1
            return col
        }

        private const val NONE = -1

        private const val PENDING = -2

        private val CONTINUATIONS = setOf(
            ".", ":.", "&&", "||", "~", "+", "/", "%", "^", "==", "!=", "<", ">", "<=", ">=", "??", "=>", "=", "+=",
            "-=", "*=", "/=", "%=", "~=", "<<=", ">>=", "->", "?", "..", "...", "<<", ">>", ",", "<=>", "<!>", "!in",
            "!is", "!of",
        )

        private val HEADERS = setOf("if", "while", "for", "else")

        private val CLOSED = setOf("{", ";", "}", ",")

        private val CONTINUATION_KEYWORDS = setOf("with", "throws", "of", "is")

        /** Whether a line starting with the token `t` continues the expression of the line before it. */
        fun isContinuation(t: YmirToken): Boolean = when (t.kind) {
            YmirToken.Kind.PUNCTUATION -> t.text in CONTINUATIONS
            YmirToken.Kind.KEYWORD -> t.text in CONTINUATION_KEYWORDS
            else -> false
        }
    }
}
