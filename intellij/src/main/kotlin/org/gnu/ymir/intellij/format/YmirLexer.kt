package org.gnu.ymir.intellij.format

/** A token of a Ymir source, a comment being one as well. */
class YmirToken(val kind: Kind, val start: Int, val end: Int, val text: String) {

    enum class Kind { IDENTIFIER, KEYWORD, NUMBER, STRING, CHAR, PUNCTUATION, LINE_COMMENT, BLOCK_COMMENT }

    val isComment: Boolean get() = kind == Kind.LINE_COMMENT || kind == Kind.BLOCK_COMMENT

    fun isOpening(): Boolean = kind == Kind.PUNCTUATION && text in OPENING

    fun isClosing(): Boolean = kind == Kind.PUNCTUATION && (text == ")" || text == "]" || text == "}")

    fun isPunctuation(t: String): Boolean = kind == Kind.PUNCTUATION && text == t

    override fun toString(): String = text

    private companion object {
        val OPENING = setOf("(", "[", "{", ":[")
    }
}

/**
 * Cuts a Ymir source in tokens as the lexer of ymirc does, without the
 * compiler: keywords, identifiers, numbers, strings, characters, operators and
 * comments, nested block comments included. A macro, `#{...}`, `#(...)` or
 * `#[...]`, is a string, as a quoted identifier is an identifier. A string or a
 * comment that is not closed runs to the end of the text.
 */
object YmirLexer {

    /** The words ymirc forbids as identifiers. */
    val KEYWORDS = setOf(
        "alias", "assert", "async", "atomic", "await", "break", "cast", "catch", "class", "const", "continue", "copy",
        "cte", "dcopy", "def", "dg", "dmut", "else", "entity", "enum", "expand", "extern", "false", "for", "fn",
        "future", "if", "impl", "in", "is", "lazy", "let", "loop", "macro", "match", "mod", "move", "mut", "none",
        "null", "of", "panic", "__pragma", "record", "ref", "return", "self", "spawn", "static", "super", "template",
        "throw", "throws", "trait", "true", "typeof", "_", "union", "__test", "unsafe", "use", "__version", "while",
        "with", "yield",
    )

    private val PUNCTUATIONS = listOf(
        "^^=", "<<=", ">>=", "<=>", "<!>", "...",
        "&?", "->", "<=", ">=", "&&", "=>", "::", "..", "==", "/=", "--", ":.", "||", "++", "^^", "??", ":[", "<<",
        "-=", "!=", "%=", "+=", ">>", "*=", "~=",
        "&", "\\", "@", "`", ":", ",", "/", "$", ".", "=", "<", "?", "{", "[", "(", "-", "!", "%", "|", "+", "}",
        "]", ")", ";", "#", "*", ">", "~", "^", "'",
    )

    private val CHAR = Regex("""'(\\(u\{[^}]*\}|.)|[^'\\])'(c8|c16|c32)?""")

    private val STRING_SUFFIXES = listOf("s8", "s16", "s32")

    private val MACROS = mapOf('{' to '}', '(' to ')', '[' to ']')

    fun tokens(text: CharSequence): List<YmirToken> {
        val res = ArrayList<YmirToken>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isWhitespace()) {
                i++
                continue
            }

            val start = i
            val kind: YmirToken.Kind
            when {
                text.startsWith("//", i) -> {
                    while (i < text.length && text[i] != '\n') i++
                    kind = YmirToken.Kind.LINE_COMMENT
                }
                text.startsWith("/*", i) -> {
                    i = blockCommentEnd(text, i)
                    kind = YmirToken.Kind.BLOCK_COMMENT
                }
                c == '"' || (c == 'f' && i + 1 < text.length && text[i + 1] == '"') -> {
                    i = stringEnd(text, if (c == 'f') i + 1 else i)
                    kind = YmirToken.Kind.STRING
                }
                c == '#' && i + 1 < text.length && text[i + 1] in MACROS -> {
                    i = macroEnd(text, i)
                    kind = YmirToken.Kind.STRING
                }
                c == '`' -> {
                    i++
                    while (i < text.length && text[i] != '`' && text[i] != '\n') i++
                    if (i < text.length && text[i] == '`') i++
                    kind = YmirToken.Kind.IDENTIFIER
                }
                c == '\'' && CHAR.matchAt(text, i) != null -> {
                    i = CHAR.matchAt(text, i)!!.range.last + 1
                    kind = YmirToken.Kind.CHAR
                }
                c.isDigit() -> {
                    i = numberEnd(text, i)
                    kind = YmirToken.Kind.NUMBER
                }
                isIdentifierStart(c) -> {
                    while (i < text.length && isIdentifierPart(text[i])) i++
                    kind = if (text.substring(start, i) in KEYWORDS) YmirToken.Kind.KEYWORD else YmirToken.Kind.IDENTIFIER
                }
                c == '!' && negatedKeywordEnd(text, i) > 0 -> {
                    i = negatedKeywordEnd(text, i)
                    kind = YmirToken.Kind.PUNCTUATION
                }
                else -> {
                    i += PUNCTUATIONS.firstOrNull { text.startsWith(it, i) }?.length ?: 1
                    kind = YmirToken.Kind.PUNCTUATION
                }
            }

            res.add(YmirToken(kind, start, i, text.substring(start, i)))
        }

        return res
    }

    fun isIdentifierPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    private fun isIdentifierStart(c: Char): Boolean = c == '_' || c.isLetter()

    private fun blockCommentEnd(text: CharSequence, from: Int): Int {
        var depth = 0
        var i = from
        while (i + 1 < text.length) {
            if (text[i] == '/' && text[i + 1] == '*') {
                depth++
                i += 2
            } else if (text[i] == '*' && text[i + 1] == '/') {
                depth--
                i += 2
                if (depth == 0) return i
            } else {
                i++
            }
        }

        return text.length
    }

    /** The end of the string whose opening quote is at `quote`, its suffix included. */
    private fun stringEnd(text: CharSequence, quote: Int): Int {
        val triple = text.startsWith("\"\"\"", quote)
        val close = if (triple) "\"\"\"" else "\""
        var i = quote + close.length
        while (i < text.length && !text.startsWith(close, i)) {
            i += if (text[i] == '\\') 2 else 1
        }

        if (i >= text.length) return text.length
        i += close.length
        return i + (STRING_SUFFIXES.firstOrNull { text.startsWith(it, i) }?.length ?: 0)
    }

    /** The end of the macro starting at `from`, the brackets it opens nested in it. */
    private fun macroEnd(text: CharSequence, from: Int): Int {
        val open = text[from + 1]
        val close = MACROS.getValue(open)
        var depth = 0
        var i = from + 1
        while (i < text.length) {
            if (text[i] == open) depth++
            else if (text[i] == close && --depth == 0) return i + 1
            i++
        }

        return text.length
    }

    private fun numberEnd(text: CharSequence, from: Int): Int {
        var i = from
        while (i < text.length && isIdentifierPart(text[i])) i++
        if (i + 1 < text.length && text[i] == '.' && text[i + 1].isDigit()) {
            i++
            while (i < text.length && isIdentifierPart(text[i])) i++
        }

        return i
    }

    /** The end of the `!in`, `!is` or `!of` at `from`, 0 if there is none. */
    private fun negatedKeywordEnd(text: CharSequence, from: Int): Int {
        for (k in listOf("in", "is", "of")) {
            val end = from + 1 + k.length
            if (text.startsWith(k, from + 1) && (end >= text.length || !isIdentifierPart(text[end]))) return end
        }

        return 0
    }
}
